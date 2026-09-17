package org.eclipse.kuksa.testapp.viewmodel

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.eclipse.kuksa.connectivity.databroker.DataBrokerConnection
import org.eclipse.kuksa.connectivity.databroker.DataBrokerConnector
import org.eclipse.kuksa.connectivity.databroker.DisconnectListener
import org.eclipse.kuksa.connectivity.databroker.v1.listener.VssNodeListener
import org.eclipse.kuksa.connectivity.databroker.v1.listener.VssPathListener
import org.eclipse.kuksa.connectivity.databroker.v1.request.FetchRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.SubscribeRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.UpdateRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.VssNodeFetchRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.VssNodeSubscribeRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.VssNodeUpdateRequest
import org.eclipse.kuksa.connectivity.databroker.v2.request.ActuateRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.BatchActuateRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.FetchValueRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.FetchValuesRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.ListMetadataRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.PublishValueRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.SubscribeByIdRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.SubscribeRequestV2
import org.eclipse.kuksa.proto.v1.KuksaValV1
import org.eclipse.kuksa.proto.v2.KuksaValV2
import org.eclipse.kuksa.testapp.domain.ConfigValidator
import org.eclipse.kuksa.testapp.domain.ValidationResult
import org.eclipse.kuksa.testapp.model.ConnectionConfig
import org.eclipse.kuksa.testapp.model.ConnectionState
import org.eclipse.kuksa.testapp.model.Direction
import org.eclipse.kuksa.testapp.model.LogEntry
import org.eclipse.kuksa.vsscore.model.VssNode
import org.eclipse.kuksa.vsscore.model.VssSignal
import org.eclipse.kuksa.proto.v1.Types as V1Types
import org.eclipse.kuksa.proto.v2.Types as V2Types

private const val MAX_LOG_ENTRIES = 1000

// Intentionally flat: each public method maps 1:1 to a DataBroker API call; splitting would obscure that symmetry.
// Exception catch blocks are at coroutine boundaries where the caller type is unknown.
@Suppress("LargeClass", "TooManyFunctions", "TooGenericExceptionCaught")
class DataBrokerViewModel(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

    private val _activeSubscriptions = MutableStateFlow<Map<String, Job>>(emptyMap())
    val activeSubscriptions: StateFlow<Map<String, Job>> = _activeSubscriptions.asStateFlow()

    private val _activeConfig = MutableStateFlow(ConnectionConfig())
    val activeConfig: StateFlow<ConnectionConfig> = _activeConfig.asStateFlow()

    internal var connection: DataBrokerConnection? = null
    private var channel: ManagedChannel? = null
    private val subscriptionJobs = mutableMapOf<String, Job>()

    var activeStreamedUpdateV1Sender: StreamObserver<KuksaValV1.StreamedUpdateRequest>? = null
    var activeProviderStreamV2Sender: StreamObserver<KuksaValV2.OpenProviderStreamRequest>? = null

    fun connect(config: ConnectionConfig) {
        val validation = ConfigValidator.validate(config)
        if (validation is ValidationResult.Error) {
            _connectionState.value = ConnectionState.ERROR
            appendLog(LogEntry(direction = Direction.ERROR, apiMethod = "connect", payload = validation.message))
            return
        }

        _activeConfig.value = config
        _connectionState.value = ConnectionState.CONNECTING
        appendLog(
            LogEntry(
                direction = Direction.REQUEST,
                apiMethod = "connect",
                payload = "Connecting to ${config.host}:${config.port}",
            ),
        )

        scope.launch {
            try {
                disconnectInternal()

                val channelBuilder = ManagedChannelBuilder.forAddress(config.host, config.port)
                if (config.useTls) {
                    channelBuilder.useTransportSecurity()
                } else {
                    channelBuilder.usePlaintext()
                }
                val builtChannel = channelBuilder.build()
                channel = builtChannel

                val jwt = if (!config.jwt.isNullOrBlank()) {
                    org.eclipse.kuksa.connectivity.authentication.JsonWebToken(config.jwt)
                } else {
                    null
                }
                val connector = DataBrokerConnector(builtChannel, jwt)

                val conn = connector.connect()
                conn.disconnectListeners.register(object : DisconnectListener {
                    override fun onDisconnect() {
                        _connectionState.value = ConnectionState.DISCONNECTED
                        appendLog(
                            LogEntry(
                                direction = Direction.EVENT,
                                apiMethod = "disconnectListener",
                                payload = "Disconnected from server",
                            ),
                        )
                    }
                })

                connection = conn
                _connectionState.value = ConnectionState.CONNECTED
                appendLog(
                    LogEntry(direction = Direction.RESPONSE, apiMethod = "connect", payload = "Connected successfully"),
                )
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                appendLog(
                    LogEntry(
                        direction = Direction.ERROR,
                        apiMethod = "connect",
                        payload = "Connection failed: ${e.message}",
                    ),
                )
            }
        }
    }

    fun disconnect() {
        scope.launch {
            disconnectInternal()
            _connectionState.value = ConnectionState.DISCONNECTED
            appendLog(
                LogEntry(direction = Direction.RESPONSE, apiMethod = "disconnect", payload = "Disconnected manually"),
            )
        }
    }

    private fun disconnectInternal() {
        subscriptionJobs.values.forEach { it.cancel() }
        subscriptionJobs.clear()
        _activeSubscriptions.value = emptyMap()

        activeStreamedUpdateV1Sender?.onCompleted()
        activeStreamedUpdateV1Sender = null

        activeProviderStreamV2Sender?.onCompleted()
        activeProviderStreamV2Sender = null

        try {
            connection?.disconnect()
        } catch (_: Exception) {
        }
        connection = null

        try {
            channel?.shutdownNow()
        } catch (_: Exception) {
        }
        channel = null
    }

    fun setConnectedStateForTesting(mockConnection: DataBrokerConnection, config: ConnectionConfig) {
        this.connection = mockConnection
        this._activeConfig.value = config
        this._connectionState.value = ConnectionState.CONNECTED
    }

    fun clearLog() {
        _logEntries.value = emptyList()
    }

    fun appendLog(entry: LogEntry) {
        val updated = (_logEntries.value + entry).takeLast(MAX_LOG_ENTRIES)
        _logEntries.value = updated
    }

    fun unsubscribe(key: String) {
        subscriptionJobs[key]?.cancel()
        subscriptionJobs.remove(key)
        _activeSubscriptions.value = subscriptionJobs.toMap()
        appendLog(LogEntry(direction = Direction.EVENT, apiMethod = "unsubscribe", payload = "Unsubscribed from $key"))
    }

    // ==========================================
    // V1 API Methods
    // ==========================================

    fun fetchV1(vssPath: String, field: V1Types.Field = V1Types.Field.FIELD_VALUE) {
        val conn = connection ?: return
        scope.launch {
            try {
                appendLog(
                    LogEntry(
                        direction = Direction.REQUEST,
                        apiMethod = "v1.fetch",
                        payload = "path: $vssPath, field: $field",
                    ),
                )
                val request = FetchRequest(vssPath, field)
                val response = conn.kuksaValV1.fetch(request)
                appendLog(
                    LogEntry(
                        direction = Direction.RESPONSE,
                        apiMethod = "v1.fetch",
                        payload = response.toString().trim(),
                    ),
                )
            } catch (e: Exception) {
                appendLog(
                    LogEntry(direction = Direction.ERROR, apiMethod = "v1.fetch", payload = e.message ?: e.toString()),
                )
            }
        }
    }

    fun <T : VssNode> fetchNodeV1(vssNode: T) {
        val conn = connection ?: return
        scope.launch {
            try {
                appendLog(
                    LogEntry(
                        direction = Direction.REQUEST,
                        apiMethod = "v1.fetchNode",
                        payload = "node: ${vssNode.vssPath}",
                    ),
                )
                val request = VssNodeFetchRequest(vssNode)
                val response = conn.kuksaValV1.fetch(request)
                appendLog(
                    LogEntry(direction = Direction.RESPONSE, apiMethod = "v1.fetchNode", payload = response.toString()),
                )
            } catch (e: Exception) {
                appendLog(
                    LogEntry(
                        direction = Direction.ERROR,
                        apiMethod = "v1.fetchNode",
                        payload = e.message ?: e.toString(),
                    ),
                )
            }
        }
    }

    fun updateV1(
        vssPath: String,
        dataPoint: V1Types.Datapoint,
        field: V1Types.Field = V1Types.Field.FIELD_VALUE,
    ) {
        val conn = connection ?: return
        scope.launch {
            try {
                appendLog(
                    LogEntry(
                        direction = Direction.REQUEST,
                        apiMethod = "v1.update",
                        payload = "path: $vssPath, datapoint: $dataPoint, field: $field",
                    ),
                )
                val request = UpdateRequest(vssPath, dataPoint, field)
                val response = conn.kuksaValV1.update(request)
                appendLog(
                    LogEntry(
                        direction = Direction.RESPONSE,
                        apiMethod = "v1.update",
                        payload = response.toString().trim(),
                    ),
                )
            } catch (e: Exception) {
                appendLog(
                    LogEntry(direction = Direction.ERROR, apiMethod = "v1.update", payload = e.message ?: e.toString()),
                )
            }
        }
    }

    fun <T : VssNode> updateNodeV1(vssNode: T) {
        val conn = connection ?: return
        scope.launch {
            try {
                val valStr = if (vssNode is VssSignal<*>) " (value: ${vssNode.value})" else ""
                appendLog(
                    LogEntry(
                        direction = Direction.REQUEST,
                        apiMethod = "v1.updateNode",
                        payload = "node: ${vssNode.vssPath}$valStr",
                    ),
                )
                val request = VssNodeUpdateRequest(vssNode)
                val response = conn.kuksaValV1.update(request)
                appendLog(
                    LogEntry(
                        direction = Direction.RESPONSE,
                        apiMethod = "v1.updateNode",
                        payload = response.toString(),
                    ),
                )
            } catch (e: Exception) {
                appendLog(
                    LogEntry(
                        direction = Direction.ERROR,
                        apiMethod = "v1.updateNode",
                        payload = e.message ?: e.toString(),
                    ),
                )
            }
        }
    }

    fun subscribeV1Listener(vssPath: String, field: V1Types.Field = V1Types.Field.FIELD_VALUE) {
        val conn = connection ?: return
        try {
            appendLog(
                LogEntry(direction = Direction.REQUEST, apiMethod = "v1.subscribeListener", payload = "path: $vssPath"),
            )
            val request = SubscribeRequest(vssPath, field)
            conn.kuksaValV1.subscribe(
                request,
                object : VssPathListener {
                    override fun onEntryChanged(entryUpdates: List<KuksaValV1.EntryUpdate>) {
                        appendLog(
                            LogEntry(
                                direction = Direction.EVENT,
                                apiMethod = "v1.onEntryChanged",
                                payload = "$vssPath updates: $entryUpdates",
                            ),
                        )
                    }

                    override fun onError(throwable: Throwable) {
                        appendLog(
                            LogEntry(
                                direction = Direction.ERROR,
                                apiMethod = "v1.subscribeListener",
                                payload = throwable.message ?: throwable.toString(),
                            ),
                        )
                    }
                },
            )
            appendLog(
                LogEntry(
                    direction = Direction.RESPONSE,
                    apiMethod = "v1.subscribeListener",
                    payload = "Listener registered for $vssPath",
                ),
            )
        } catch (e: Exception) {
            appendLog(
                LogEntry(
                    direction = Direction.ERROR,
                    apiMethod = "v1.subscribeListener",
                    payload = e.message ?: e.toString(),
                ),
            )
        }
    }

    fun subscribeV1Flow(vssPath: String, field: V1Types.Field = V1Types.Field.FIELD_VALUE) {
        val conn = connection ?: return
        val key = "v1-flow-$vssPath"
        subscriptionJobs[key]?.cancel()

        val job = scope.launch {
            try {
                appendLog(
                    LogEntry(direction = Direction.REQUEST, apiMethod = "v1.subscribeFlow", payload = "path: $vssPath"),
                )
                val request = SubscribeRequest(vssPath, field)
                val flow = conn.kuksaValV1.subscribe(request)
                flow.collect { response ->
                    appendLog(
                        LogEntry(
                            direction = Direction.EVENT,
                            apiMethod = "v1.subscribeFlow",
                            payload = response.toString().trim(),
                        ),
                    )
                }
            } catch (e: Exception) {
                appendLog(
                    LogEntry(
                        direction = Direction.ERROR,
                        apiMethod = "v1.subscribeFlow",
                        payload = e.message ?: e.toString(),
                    ),
                )
            }
        }
        subscriptionJobs[key] = job
        _activeSubscriptions.value = subscriptionJobs.toMap()
    }

    fun <T : VssNode> subscribeNodeV1(vssNode: T) {
        val conn = connection ?: return
        try {
            appendLog(
                LogEntry(
                    direction = Direction.REQUEST,
                    apiMethod = "v1.subscribeNode",
                    payload = "node: ${vssNode.vssPath}",
                ),
            )
            val request = VssNodeSubscribeRequest(vssNode)
            conn.kuksaValV1.subscribe(
                request,
                object : VssNodeListener<T> {
                    override fun onNodeChanged(vssNode: T) {
                        appendLog(
                            LogEntry(
                                direction = Direction.EVENT,
                                apiMethod = "v1.onNodeChanged",
                                payload = vssNode.toString(),
                            ),
                        )
                    }

                    override fun onError(throwable: Throwable) {
                        appendLog(
                            LogEntry(
                                direction = Direction.ERROR,
                                apiMethod = "v1.subscribeNode",
                                payload = throwable.message ?: throwable.toString(),
                            ),
                        )
                    }
                },
            )
            appendLog(
                LogEntry(
                    direction = Direction.RESPONSE,
                    apiMethod = "v1.subscribeNode",
                    payload = "Node listener registered for ${vssNode.vssPath}",
                ),
            )
        } catch (e: Exception) {
            appendLog(
                LogEntry(
                    direction = Direction.ERROR,
                    apiMethod = "v1.subscribeNode",
                    payload = e.message ?: e.toString(),
                ),
            )
        }
    }

    fun openStreamedUpdateV1(
        receiverObserver: StreamObserver<KuksaValV1.StreamedUpdateResponse>? = null,
    ): StreamObserver<KuksaValV1.StreamedUpdateRequest>? {
        val conn = connection ?: return null
        return try {
            val observer = receiverObserver ?: object : StreamObserver<KuksaValV1.StreamedUpdateResponse> {
                override fun onNext(value: KuksaValV1.StreamedUpdateResponse) {
                    appendLog(
                        LogEntry(
                            direction = Direction.EVENT,
                            apiMethod = "v1.streamedUpdate",
                            payload = value.toString().trim(),
                        ),
                    )
                }

                override fun onError(t: Throwable) {
                    appendLog(
                        LogEntry(
                            direction = Direction.ERROR,
                            apiMethod = "v1.streamedUpdate",
                            payload = t.message ?: t.toString(),
                        ),
                    )
                }

                override fun onCompleted() {
                    appendLog(
                        LogEntry(
                            direction = Direction.EVENT,
                            apiMethod = "v1.streamedUpdate",
                            payload = "Stream completed",
                        ),
                    )
                }
            }
            val sender = conn.kuksaValV1.streamedUpdate(observer)
            activeStreamedUpdateV1Sender = sender
            appendLog(
                LogEntry(
                    direction = Direction.RESPONSE,
                    apiMethod = "v1.streamedUpdate",
                    payload = "StreamedUpdate opened",
                ),
            )
            sender
        } catch (e: Exception) {
            appendLog(
                LogEntry(
                    direction = Direction.ERROR,
                    apiMethod = "v1.streamedUpdate",
                    payload = e.message ?: e.toString(),
                ),
            )
            null
        }
    }

    // ==========================================
    // V2 API Methods
    // ==========================================

    fun fetchValueV2(signalId: V2Types.SignalID) {
        val conn = connection ?: return
        scope.launch {
            try {
                appendLog(
                    LogEntry(
                        direction = Direction.REQUEST,
                        apiMethod = "v2.fetchValue",
                        payload = signalId.toString().trim(),
                    ),
                )
                val request = FetchValueRequestV2(signalId)
                val response = conn.kuksaValV2.fetchValue(request)
                appendLog(
                    LogEntry(
                        direction = Direction.RESPONSE,
                        apiMethod = "v2.fetchValue",
                        payload = response.toString().trim(),
                    ),
                )
            } catch (e: Exception) {
                appendLog(
                    LogEntry(
                        direction = Direction.ERROR,
                        apiMethod = "v2.fetchValue",
                        payload = e.message ?: e.toString(),
                    ),
                )
            }
        }
    }

    fun fetchValuesV2(signalIds: List<V2Types.SignalID>) {
        val conn = connection ?: return
        scope.launch {
            try {
                appendLog(
                    LogEntry(
                        direction = Direction.REQUEST,
                        apiMethod = "v2.fetchValues",
                        payload = "Count: ${signalIds.size}",
                    ),
                )
                val request = FetchValuesRequestV2(signalIds)
                val response = conn.kuksaValV2.fetchValues(request)
                appendLog(
                    LogEntry(
                        direction = Direction.RESPONSE,
                        apiMethod = "v2.fetchValues",
                        payload = response.toString().trim(),
                    ),
                )
            } catch (e: Exception) {
                appendLog(
                    LogEntry(
                        direction = Direction.ERROR,
                        apiMethod = "v2.fetchValues",
                        payload = e.message ?: e.toString(),
                    ),
                )
            }
        }
    }

    fun publishValueV2(signalId: V2Types.SignalID, datapoint: V2Types.Datapoint) {
        val conn = connection ?: return
        scope.launch {
            try {
                appendLog(
                    LogEntry(
                        direction = Direction.REQUEST,
                        apiMethod = "v2.publishValue",
                        payload = "signalId: ${signalId.path}, value: $datapoint",
                    ),
                )
                val request = PublishValueRequestV2(signalId, datapoint)
                val response = conn.kuksaValV2.publishValue(request)
                appendLog(
                    LogEntry(
                        direction = Direction.RESPONSE,
                        apiMethod = "v2.publishValue",
                        payload = response.toString().trim(),
                    ),
                )
            } catch (e: Exception) {
                appendLog(
                    LogEntry(
                        direction = Direction.ERROR,
                        apiMethod = "v2.publishValue",
                        payload = e.message ?: e.toString(),
                    ),
                )
            }
        }
    }

    fun actuateV2(signalId: V2Types.SignalID, value: V2Types.Value) {
        val conn = connection ?: return
        scope.launch {
            try {
                appendLog(
                    LogEntry(
                        direction = Direction.REQUEST,
                        apiMethod = "v2.actuate",
                        payload = "signalId: ${signalId.path}, value: $value",
                    ),
                )
                val request = ActuateRequestV2(signalId, value)
                val response = conn.kuksaValV2.actuate(request)
                appendLog(
                    LogEntry(
                        direction = Direction.RESPONSE,
                        apiMethod = "v2.actuate",
                        payload = response.toString().trim(),
                    ),
                )
            } catch (e: Exception) {
                val errorMsg = if (e.message?.contains("UNAVAILABLE") == true) {
                    "No provider: run ./gradlew :mock-provider:run"
                } else {
                    e.message ?: e.toString()
                }
                appendLog(LogEntry(direction = Direction.ERROR, apiMethod = "v2.actuate", payload = errorMsg))
            }
        }
    }

    fun batchActuateV2(signalIds: List<V2Types.SignalID>, value: V2Types.Value) {
        val conn = connection ?: return
        scope.launch {
            try {
                appendLog(
                    LogEntry(
                        direction = Direction.REQUEST,
                        apiMethod = "v2.batchActuate",
                        payload = "signalIds: ${signalIds.map { it.path }}, value: $value",
                    ),
                )
                val request = BatchActuateRequestV2(signalIds, value)
                val response = conn.kuksaValV2.batchActuate(request)
                appendLog(
                    LogEntry(
                        direction = Direction.RESPONSE,
                        apiMethod = "v2.batchActuate",
                        payload = response.toString().trim(),
                    ),
                )
            } catch (e: Exception) {
                appendLog(
                    LogEntry(
                        direction = Direction.ERROR,
                        apiMethod = "v2.batchActuate",
                        payload = e.message ?: e.toString(),
                    ),
                )
            }
        }
    }

    fun subscribeByPathV2(paths: List<String>, bufferSize: Int = 10) {
        val conn = connection ?: return
        val key = "v2-path-${paths.joinToString(",")}"
        subscriptionJobs[key]?.cancel()

        val job = scope.launch {
            try {
                appendLog(
                    LogEntry(
                        direction = Direction.REQUEST,
                        apiMethod = "v2.subscribePath",
                        payload = "paths: $paths, buffer: $bufferSize",
                    ),
                )
                val request = SubscribeRequestV2(paths, bufferSize)
                val flow = conn.kuksaValV2.subscribe(request)
                flow.collect { response ->
                    appendLog(
                        LogEntry(
                            direction = Direction.EVENT,
                            apiMethod = "v2.subscribePath",
                            payload = response.toString().trim(),
                        ),
                    )
                }
            } catch (e: Exception) {
                appendLog(
                    LogEntry(
                        direction = Direction.ERROR,
                        apiMethod = "v2.subscribePath",
                        payload = e.message ?: e.toString(),
                    ),
                )
            }
        }
        subscriptionJobs[key] = job
        _activeSubscriptions.value = subscriptionJobs.toMap()
    }

    fun subscribeByIdV2(signalIds: List<Int>, bufferSize: Int = 10) {
        val conn = connection ?: return
        val key = "v2-id-${signalIds.joinToString(",")}"
        subscriptionJobs[key]?.cancel()

        val job = scope.launch {
            try {
                appendLog(
                    LogEntry(
                        direction = Direction.REQUEST,
                        apiMethod = "v2.subscribeId",
                        payload = "ids: $signalIds, buffer: $bufferSize",
                    ),
                )
                val request = SubscribeByIdRequestV2(signalIds, bufferSize)
                val flow = conn.kuksaValV2.subscribeById(request)
                flow.collect { response ->
                    appendLog(
                        LogEntry(
                            direction = Direction.EVENT,
                            apiMethod = "v2.subscribeId",
                            payload = response.toString().trim(),
                        ),
                    )
                }
            } catch (e: Exception) {
                appendLog(
                    LogEntry(
                        direction = Direction.ERROR,
                        apiMethod = "v2.subscribeId",
                        payload = e.message ?: e.toString(),
                    ),
                )
            }
        }
        subscriptionJobs[key] = job
        _activeSubscriptions.value = subscriptionJobs.toMap()
    }

    fun openProviderStreamV2(
        receiverObserver: StreamObserver<KuksaValV2.OpenProviderStreamResponse>? = null,
    ): StreamObserver<KuksaValV2.OpenProviderStreamRequest>? {
        val conn = connection ?: return null
        return try {
            val observer = receiverObserver ?: object : StreamObserver<KuksaValV2.OpenProviderStreamResponse> {
                override fun onNext(value: KuksaValV2.OpenProviderStreamResponse) {
                    appendLog(
                        LogEntry(
                            direction = Direction.EVENT,
                            apiMethod = "v2.openProviderStream",
                            payload = value.toString().trim(),
                        ),
                    )
                }

                override fun onError(t: Throwable) {
                    appendLog(
                        LogEntry(
                            direction = Direction.ERROR,
                            apiMethod = "v2.openProviderStream",
                            payload = t.message ?: t.toString(),
                        ),
                    )
                }

                override fun onCompleted() {
                    appendLog(
                        LogEntry(
                            direction = Direction.EVENT,
                            apiMethod = "v2.openProviderStream",
                            payload = "Stream completed",
                        ),
                    )
                }
            }
            val sender = conn.kuksaValV2.openProviderStream(observer)
            activeProviderStreamV2Sender = sender
            appendLog(
                LogEntry(
                    direction = Direction.RESPONSE,
                    apiMethod = "v2.openProviderStream",
                    payload = "Provider stream opened",
                ),
            )
            sender
        } catch (e: Exception) {
            appendLog(
                LogEntry(
                    direction = Direction.ERROR,
                    apiMethod = "v2.openProviderStream",
                    payload = e.message ?: e.toString(),
                ),
            )
            null
        }
    }

    fun listMetadataV2(root: String = "Vehicle", filter: String = "") {
        val conn = connection ?: return
        scope.launch {
            try {
                appendLog(
                    LogEntry(
                        direction = Direction.REQUEST,
                        apiMethod = "v2.listMetadata",
                        payload = "root: $root, filter: $filter",
                    ),
                )
                val request = ListMetadataRequestV2(root, filter)
                val response = conn.kuksaValV2.listMetadata(request)
                appendLog(
                    LogEntry(
                        direction = Direction.RESPONSE,
                        apiMethod = "v2.listMetadata",
                        payload = "Found ${response.metadataCount} metadata entries",
                    ),
                )
            } catch (e: Exception) {
                appendLog(
                    LogEntry(
                        direction = Direction.ERROR,
                        apiMethod = "v2.listMetadata",
                        payload = e.message ?: e.toString(),
                    ),
                )
            }
        }
    }

    fun fetchServerInfoV2() {
        val conn = connection ?: return
        scope.launch {
            try {
                appendLog(
                    LogEntry(
                        direction = Direction.REQUEST,
                        apiMethod = "v2.fetchServerInfo",
                        payload = "Fetching server info...",
                    ),
                )
                val response = conn.kuksaValV2.fetchServerInfo()
                appendLog(
                    LogEntry(
                        direction = Direction.RESPONSE,
                        apiMethod = "v2.fetchServerInfo",
                        payload = "Server: ${response.name}, Version: ${response.version}",
                    ),
                )
            } catch (e: Exception) {
                appendLog(
                    LogEntry(
                        direction = Direction.ERROR,
                        apiMethod = "v2.fetchServerInfo",
                        payload = e.message ?: e.toString(),
                    ),
                )
            }
        }
    }
}
