/*
 * Copyright (c) 2023 - 2025 Contributors to the Eclipse Foundation
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
 *
 */

package org.eclipse.kuksa.connectivity.databroker.v1

import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.eclipse.kuksa.connectivity.authentication.JsonWebToken
import org.eclipse.kuksa.connectivity.databroker.DataBrokerException
import org.eclipse.kuksa.connectivity.databroker.v1.listener.VssNodeListener
import org.eclipse.kuksa.connectivity.databroker.v1.listener.VssPathListener
import org.eclipse.kuksa.connectivity.databroker.v1.request.FetchRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.SubscribeRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.UpdateRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.VssNodeFetchRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.VssNodeSubscribeRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.VssNodeUpdateRequest
import org.eclipse.kuksa.connectivity.databroker.v1.response.VssNodeUpdateResponse
import org.eclipse.kuksa.connectivity.databroker.v1.subscription.VssNodePathListener
import org.eclipse.kuksa.extension.TAG
import org.eclipse.kuksa.extension.datapoint
import org.eclipse.kuksa.extension.vss.copy
import org.eclipse.kuksa.proto.v1.KuksaValV1
import org.eclipse.kuksa.proto.v1.KuksaValV1.GetResponse
import org.eclipse.kuksa.proto.v1.KuksaValV1.SetResponse
import org.eclipse.kuksa.proto.v1.Types
import org.eclipse.kuksa.proto.v1.Types.Datapoint
import org.eclipse.kuksa.vsscore.model.VssNode
import org.eclipse.kuksa.vsscore.model.VssSignal
import org.eclipse.kuksa.vsscore.model.heritage
import org.eclipse.kuksa.vsscore.model.vssSignals
import java.util.logging.Logger
import kotlin.properties.Delegates

/**
 * The DataBrokerConnection holds an active connection to the DataBroker. The Connection can be use to interact with the
 * DataBroker.
 */
class KuksaValV1Protocol internal constructor(
    private val managedChannel: ManagedChannel,
    private val dataBrokerInvokerV1: DataBrokerInvokerV1 = DataBrokerInvokerV1(
        managedChannel,
    ),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val logger = Logger.getLogger(TAG)

    @Suppress("unused") // property is propagated to the transporter
    internal var jsonWebToken: JsonWebToken? by Delegates.observable(null) { _, _, newValue ->
        dataBrokerInvokerV1.jsonWebToken = newValue
    }

    /**
     * Subscribes to the specified [request] and notifies the provided [listener] about updates.
     *
     * Throws a [DataBrokerException] in case the connection to the DataBroker is no longer active
     */
    fun subscribe(
        request: SubscribeRequest,
        listener: VssPathListener,
    ) {
        val vssPath = request.vssPath
        val fields = request.fields.toList()

        dataBrokerInvokerV1.subscribe(vssPath, fields, listener)
    }

    /**
     * Subscribes to the specified [request] and returns a flow with provides the corresponding updates.
     *
     * Throws a [DataBrokerException] in case the connection to the DataBroker is no longer active
     * Throws a [io.grpc.StatusException] when an error occurs while collecting the flow.
     */
    fun subscribe(
        request: SubscribeRequest,
    ): Flow<KuksaValV1.SubscribeResponse> {
        val vssPath = request.vssPath
        val fields = request.fields.toList()

        return dataBrokerInvokerV1.subscribe(vssPath, fields)
    }

    /**
     * Subscribes to the specified [VssNode] with the provided [VssNodeListener]. Only a [VssSignal]
     * can be subscribed because they have an actual value. When provided with any parent [VssNode] then this
     * [subscribe] method will find all [VssSignal] children and subscribes them instead. Once subscribed the
     * application will be notified about any changes to every subscribed [VssSignal].
     * The [VssNodeSubscribeRequest.fields] can be used to subscribe to different information of the [VssNode].
     * The default for the [Types.Field] parameter is a list with a single [Types.Field.FIELD_VALUE] entry.
     *
     * @throws DataBrokerException in case the connection to the DataBroker is no longer active
     */
    fun <T : VssNode> subscribe(
        request: VssNodeSubscribeRequest<T>,
        listener: VssNodeListener<T>,
    ) {
        val vssPath = request.vssPath
        val vssNode = request.vssNode
        val fields = request.fields.toList()

        val vssNodePathListener = VssNodePathListener(vssNode, listener)
        dataBrokerInvokerV1.subscribe(vssPath, fields, vssNodePathListener)
    }

    /**
     * Retrieves the underlying data broker information of the specified vssPath and returns it to the corresponding
     * callback.
     *
     * @throws DataBrokerException in case the connection to the DataBroker is no longer active
     */
    suspend fun fetch(request: FetchRequest): GetResponse {
        logger.finer("Fetching via request: $request")
        return dataBrokerInvokerV1.fetch(request.vssPath, request.fields.toSet())
    }

    /**
     * Retrieves the [VssNode] and returns it. The retrieved [VssNode]
     * is of the same type as the inputted one. All underlying heirs are changed to reflect the data broker state.
     *
     * @throws DataBrokerException in case the connection to the DataBroker is no longer active
     */

    // SpreadOperator: Neglectable - Field types are 1-2 elements mostly
    // TooGenericExceptionCaught: Handling is bundled together
    @Suppress("exceptions:TooGenericExceptionCaught", "performance:SpreadOperator")
    suspend fun <T : VssNode> fetch(request: VssNodeFetchRequest<T>): T {
        return withContext(dispatcher) {
            try {
                val vssNode = request.vssNode
                val simpleFetchRequest = FetchRequest(request.vssPath, *request.fields)
                val response = fetch(simpleFetchRequest)
                val entries = response.entriesList

                if (entries.isEmpty()) {
                    logger.warning("No entries found for fetched VssNode!")
                    return@withContext vssNode
                }

                // Update every heir node
                // TODO: Can be optimized to not replace the whole heritage line for every child entry one by one
                var updatedVssNode: T = vssNode
                val heritage = updatedVssNode.heritage
                entries.forEach { entry ->
                    updatedVssNode = updatedVssNode.copy(entry.path, entry.value, heritage)
                }

                return@withContext updatedVssNode
            } catch (e: Exception) {
                throw DataBrokerException(e.message, e)
            }
        }
    }

    /**
     * Updates the underlying data broker property of the specified [UpdateRequest.vssPath] with the
     * [UpdateRequest.dataPoint]. Notifies the callback about (un)successful operation.
     *
     * @throws DataBrokerException in case the connection to the DataBroker is no longer active
     */
    suspend fun update(request: UpdateRequest): SetResponse {
        logger.finer("Update with request: $request")
        return dataBrokerInvokerV1.update(request.vssPath, request.dataPoint, request.fields.toSet())
    }

    /**
     * Allows the provider to continuously send updates to the DataBroker.
     * @param receiverStream the receiverStream which will be notified about the responses.
     *
     * @return the senderStream, which can be used to emit new requests.
     */
    fun streamedUpdate(
        receiverStream: StreamObserver<KuksaValV1.StreamedUpdateResponse>,
    ): StreamObserver<KuksaValV1.StreamedUpdateRequest> {
        return dataBrokerInvokerV1.streamedUpdate(receiverStream)
    }

    /**
     * Only a [VssSignal] can be updated because they have an actual value. When provided with any parent
     * [VssNode] then this [update] method will find all [VssSignal] children and updates their corresponding
     * [Types.Field] instead.
     * Compared to [update] with only one [UpdateRequest], here multiple [SetResponse] via [VssNodeUpdateResponse] will
     * be returned because a [VssNode] may consists of multiple values which may need to be updated.
     *
     * @throws DataBrokerException in case the connection to the DataBroker is no longer active
     * @throws IllegalArgumentException if the [VssSignal] could not be converted to a [Datapoint].
     */
    @Suppress("performance:SpreadOperator") // Neglectable: Field types are 1-2 elements mostly
    suspend fun <T : VssNode> update(request: VssNodeUpdateRequest<T>): VssNodeUpdateResponse {
        val responses = mutableListOf<SetResponse>()
        val vssNode = request.vssNode

        vssNode.vssSignals.forEach { signal ->
            val simpleUpdateRequest = UpdateRequest(signal.vssPath, signal.datapoint, *request.fields)
            val response = update(simpleUpdateRequest)

            responses.add(response)
        }

        return VssNodeUpdateResponse(responses)
    }
}
