/*
 * Copyright (c) 2023 - 2026 Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.kuksa.mockprovider.provider

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.kuksa.connectivity.databroker.DataBrokerConnection
import org.eclipse.kuksa.connectivity.databroker.DataBrokerConnector
import org.eclipse.kuksa.connectivity.databroker.v2.request.FetchValueRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.ListMetadataRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.PublishValueRequestV2
import org.eclipse.kuksa.mockprovider.discovery.VssActuatorDiscovery
import org.eclipse.kuksa.mockprovider.engine.BehaviorRuleEngine
import org.eclipse.kuksa.mockprovider.engine.RuleLoader
import org.eclipse.kuksa.mockprovider.model.BehaviorRule
import org.eclipse.kuksa.mockprovider.model.Transform
import org.eclipse.kuksa.mockprovider.web.DashboardState
import org.eclipse.kuksa.proto.v2.KuksaValV2
import org.eclipse.kuksa.proto.v2.Types
import java.io.File
import org.eclipse.kuksa.connectivity.databroker.v1.request.SubscribeRequest as SubscribeRequestV1
import org.eclipse.kuksa.connectivity.databroker.v1.request.UpdateRequest as UpdateRequestV1
import org.eclipse.kuksa.proto.v1.Types as V1Types

data class MockProviderConfig(
    val host: String = "localhost",
    val port: Int = 55556,
    val rulesPath: String = "mock-environment/behavior-rules.yaml",
    val vssDirectoryPath: String = "vss",
    val vssFilePath: String? = null,
    val verbose: Boolean = false,
)

// The class groups tightly coupled lifecycle, stream-handling, and telemetry functions that
// cannot be split without introducing artificial indirection or shared mutable state across classes.
@Suppress("TooManyFunctions")
class MockProvider(
    val config: MockProviderConfig = MockProviderConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val dashboardState: DashboardState? = null,
) {
    val host: String get() = config.host
    val port: Int get() = config.port
    val rulesPath: String get() = config.rulesPath
    val vssDirectoryPath: String get() = config.vssDirectoryPath
    val vssFilePath: String? get() = config.vssFilePath
    val verbose: Boolean get() = config.verbose

    val engine: BehaviorRuleEngine = BehaviorRuleEngine(RuleLoader.load(rulesPath))
    private var channel: ManagedChannel? = null
    private var connection: DataBrokerConnection? = null
    private var requestStream: StreamObserver<KuksaValV2.OpenProviderStreamRequest>? = null
    private val idToPath = mutableMapOf<Int, String>()

    suspend fun start() {
        info("Connecting to DataBroker at $host:$port (Rules: ${engine.rules.size} configured)...")
        val managedChannel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()
        channel = managedChannel

        val connector = DataBrokerConnector(managedChannel)
        val dataBrokerConnection = connector.connect()
        connection = dataBrokerConnection
        info("Connected to DataBroker.")

        startV2ProviderStream(dataBrokerConnection)
        startV1TargetReflector(dataBrokerConnection)
    }

    private fun startV2ProviderStream(dataBrokerConnection: DataBrokerConnection) {
        val responseObserver = object : StreamObserver<KuksaValV2.OpenProviderStreamResponse> {
            override fun onNext(response: KuksaValV2.OpenProviderStreamResponse) {
                if (response.hasBatchActuateStreamRequest()) {
                    handleBatchActuate(response.batchActuateStreamRequest)
                }
            }

            override fun onError(t: Throwable) {
                error("v2 Provider stream error: ${t.message}")
            }

            override fun onCompleted() {
                info("v2 Provider stream completed.")
            }
        }

        val stream = dataBrokerConnection.kuksaValV2.openProviderStream(responseObserver)
        requestStream = stream

        val vssDir = File(vssDirectoryPath)
        val vssFile = vssFilePath?.let { File(it) }
        val discoveredActuators = runCatching {
            VssActuatorDiscovery.discoverActuatorPaths(vssDirectory = vssDir, vssFile = vssFile)
        }.getOrDefault(emptyList())
        val yamlActuators = engine.rules.map { it.actuator }.filter { !it.contains("*") }
        val allActuators = (discoveredActuators + yamlActuators).distinct()

        dashboardState?.registerActuators(allActuators, engine)

        val signalIds = allActuators.map { path ->
            Types.SignalID.newBuilder().setPath(path).build()
        }
        val provideActuationRequest = KuksaValV2.ProvideActuationRequest.newBuilder()
            .addAllActuatorIdentifiers(signalIds)
            .build()
        val openStreamReq = KuksaValV2.OpenProviderStreamRequest.newBuilder()
            .setProvideActuationRequest(provideActuationRequest)
            .build()
        stream.onNext(openStreamReq)

        dashboardState?.streamActive = true

        info(
            "Registered ${allActuators.size} actuator paths for v2 actuation (from VSS: ${discoveredActuators.size}).",
        )

        scope.launch { buildIdToPathMap(dataBrokerConnection, allActuators) }
    }

    private suspend fun buildIdToPathMap(connection: DataBrokerConnection, paths: List<String>) {
        info("Resolving signal IDs for ${paths.size} actuators...")
        for (path in paths) {
            runCatching {
                val response = connection.kuksaValV2.listMetadata(ListMetadataRequestV2(root = path))
                response.metadataList.firstOrNull()?.let { idToPath[it.id] = path }
            }
        }
        info("ID→path map built for ${idToPath.size}/${paths.size} actuators.")
    }

    private fun resolveSignalPath(signalId: Types.SignalID): String {
        return when {
            signalId.hasPath() -> signalId.path
            signalId.hasId() -> idToPath[signalId.id] ?: run {
                error("Signal ID ${signalId.id} not yet in ID→path map (map still building?)")
                ""
            }
            else -> ""
        }
    }

    // Coroutine boundary: any exception from the flow or gRPC layer should stop the reflector gracefully.
    @Suppress("TooGenericExceptionCaught")
    private fun startV1TargetReflector(dataBrokerConnection: DataBrokerConnection) {
        scope.launch {
            try {
                val subscribeReq = SubscribeRequestV1(
                    vssPath = "Vehicle",
                    fields = arrayOf(V1Types.Field.FIELD_ACTUATOR_TARGET),
                )
                val flow = dataBrokerConnection.kuksaValV1.subscribe(subscribeReq)
                info("v1 target reflector active for 'Vehicle' subtree.")
                flow.collect { response ->
                    for (update in response.updatesList) {
                        val entry = update.entry
                        val path = entry.path
                        if (entry.hasActuatorTarget()) {
                            val targetDatapoint = entry.actuatorTarget
                            val rule = engine.resolveRule(path)
                            val targetSensor = engine.resolveTargetSensor(rule, path)
                            scope.launch {
                                if (rule.delayMs > 0) delay(rule.delayMs)
                                runCatching {
                                    dataBrokerConnection.kuksaValV1.update(
                                        UpdateRequestV1(
                                            vssPath = targetSensor,
                                            dataPoint = targetDatapoint,
                                            fields = arrayOf(V1Types.Field.FIELD_VALUE),
                                        ),
                                    )
                                    info(
                                        "[v1 ACTUATE SUCCESS] Reflected $targetSensor target -> " +
                                            "FIELD_VALUE ($targetDatapoint)",
                                    )
                                }.onFailure {
                                    error(
                                        "[v1 ACTUATE ERROR] Update failed for $targetSensor: ${it.message}",
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                info("v1 target reflector stopped: ${e.message}")
            }
        }
    }

    private fun handleBatchActuate(batchActuate: KuksaValV2.BatchActuateStreamRequest) {
        for (req in batchActuate.actuateRequestsList) {
            val signalId = req.signalId
            val ack = KuksaValV2.OpenProviderStreamRequest.newBuilder()
                .setBatchActuateStreamResponse(
                    KuksaValV2.BatchActuateStreamResponse.newBuilder()
                        .setSignalId(signalId)
                        .build(),
                )
                .build()
            requestStream?.onNext(ack)

            val actuatorPath = resolveSignalPath(signalId)
            val rule = engine.resolveRule(actuatorPath)
            val targetSensor = engine.resolveTargetSensor(rule, actuatorPath)

            dashboardState?.actuationsProcessed?.incrementAndGet()
            dashboardState?.log(
                "ACTUATE",
                "Received actuation for $actuatorPath",
                mapOf(
                    "value" to req.value.toString().trim(),
                    "rule" to if (engine.findRule(actuatorPath) == null) {
                        "<Generic Passthrough> (auto-forward)"
                    } else {
                        "Rule: ${rule.transform} (${rule.delayMs}ms)"
                    },
                ),
            )

            if (verbose) {
                println("[v2 ACTUATE REQ] Received actuation for $actuatorPath -> ${req.value}")
            }
            scope.launch {
                applyRule(rule, targetSensor, req.value)
            }
        }
    }

    private suspend fun applyRule(rule: BehaviorRule, targetSensor: String, targetValue: Types.Value) {
        val conn = connection ?: return
        when (rule.transform) {
            Transform.DIRECT -> {
                if (rule.delayMs > 0) delay(rule.delayMs)
                val directValue = engine.computeDirectValue(rule, targetValue)
                publishSensorValue(targetSensor, directValue)
            }
            Transform.RAMP -> {
                val currentDatapoint = runCatching {
                    val fetchReq = FetchValueRequestV2(Types.SignalID.newBuilder().setPath(targetSensor).build())
                    conn.kuksaValV2.fetchValue(fetchReq).dataPoint
                }.getOrNull()
                val currentFloat = currentDatapoint?.value?.float ?: 0.0f
                val targetFloat = targetValue.float
                val steps = engine.computeRampSteps(rule, currentFloat, targetFloat, RAMP_STEPS)
                val stepDelay = (rule.delayMs / RAMP_STEPS).coerceAtLeast(MIN_STEP_DELAY_MS)
                for (step in steps) {
                    delay(stepDelay)
                    val stepVal = Types.Value.newBuilder().setFloat(step).build()
                    publishSensorValue(targetSensor, stepVal)
                }
            }
            Transform.TOGGLE -> {
                if (rule.delayMs > 0) delay(rule.delayMs)
                val currentDatapoint = runCatching {
                    val fetchReq = FetchValueRequestV2(Types.SignalID.newBuilder().setPath(targetSensor).build())
                    conn.kuksaValV2.fetchValue(fetchReq).dataPoint
                }.getOrNull()
                val currentVal = currentDatapoint?.value ?: Types.Value.newBuilder().setBool(false).build()
                val toggled = engine.computeToggle(rule, currentVal)
                publishSensorValue(targetSensor, toggled)
            }
        }
    }

    private suspend fun publishSensorValue(sensorPath: String, value: Types.Value) {
        val conn = connection ?: return
        val signalId = Types.SignalID.newBuilder().setPath(sensorPath).build()
        val datapoint = Types.Datapoint.newBuilder().setValue(value).build()
        runCatching {
            conn.kuksaValV2.publishValue(PublishValueRequestV2(signalId, datapoint))
            val msg = "[v2 ACTUATE SUCCESS] Published value $value to DataBroker for $sensorPath"
            println(msg)
            dashboardState?.log(
                "SUCCESS",
                msg,
                mapOf("result" to "Published OK"),
            )
        }.onFailure {
            error("[v2 ACTUATE ERROR] Failed to publish sensor value for $sensorPath: ${it.message}")
        }
    }

    private fun info(message: String) {
        println(message)
        dashboardState?.log("INFO", message)
    }

    private fun error(message: String) {
        println(message)
        dashboardState?.log("ERROR", message)
    }

    companion object {
        private const val RAMP_STEPS = 10
        private const val MIN_STEP_DELAY_MS = 10L
    }

    fun stop() {
        requestStream?.onCompleted()
        connection?.disconnect()
        channel?.shutdownNow()
    }
}
