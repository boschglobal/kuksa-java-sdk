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

package org.eclipse.kuksa.connectivity.databroker.v2

import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import kotlinx.coroutines.flow.Flow
import org.eclipse.kuksa.connectivity.authentication.JsonWebToken
import org.eclipse.kuksa.connectivity.databroker.DisconnectListener
import org.eclipse.kuksa.extension.TAG
import org.eclipse.kuksa.pattern.listener.MultiListener
import org.eclipse.kuksa.proto.v2.KuksaValV2
import org.eclipse.kuksa.proto.v2.Types
import org.eclipse.kuksa.proto.v2.Types.Value
import java.util.logging.Logger
import kotlin.properties.Delegates

class DataBrokerConnectionV2 internal constructor(
    private val managedChannel: ManagedChannel,
    private val dataBrokerTransporter: DataBrokerTransporterV2 = DataBrokerTransporterV2(managedChannel),
) {
    private val logger = Logger.getLogger(TAG)

    /**
     * Used to register and unregister multiple [DisconnectListener].
     */
    val disconnectListeners = MultiListener<DisconnectListener>()

    /**
     * A JsonWebToken can be provided to authenticate against the DataBroker.
     */
    var jsonWebToken: JsonWebToken? by Delegates.observable(null) { _, _, newValue ->
        dataBrokerTransporter.jsonWebToken = newValue
    }

    init {
        val state = managedChannel.getState(false)
        managedChannel.notifyWhenStateChanged(state) {
            val newState = managedChannel.getState(false)
            logger.finer("DataBrokerConnection state changed: $newState")
            if (newState != ConnectivityState.SHUTDOWN) {
                managedChannel.shutdownNow()
            }

            disconnectListeners.forEach { listener ->
                listener.onDisconnect()
            }
        }
    }

    /**
     * Gets the latest value of a [signalId].
     *
     * The server might respond with the following GRPC error codes:
     *    NOT_FOUND if the requested signal doesn't exist
     *    PERMISSION_DENIED if access is denied
     */
    suspend fun fetchValue(signalId: Types.SignalID): KuksaValV2.GetValueResponse {
        return dataBrokerTransporter.fetchValue(signalId)
    }

    /**
     * Gets the latest values of a set of [signalIds]. The returned list of data points has the same order as the list
     * of the request.
     *
     * The server might respond with the following GRPC error codes:
     *    NOT_FOUND if any of the requested signals doesn't exist.
     *    PERMISSION_DENIED if access is denied for any of the requested signals.
     */
    suspend fun fetchValues(signalIds: List<Types.SignalID>): KuksaValV2.GetValuesResponse {
        return dataBrokerTransporter.fetchValues(signalIds)
    }

    /**
     * Subscribe to a set of signals using i32 id parameters
     * Returns (GRPC error code):
     *    NOT_FOUND if any of the signals are non-existent.
     *    PERMISSION_DENIED if access is denied for any of the signals.
     */
    fun subscribeById(
        signalIds: List<Int>,
    ): Flow<KuksaValV2.SubscribeByIdResponse> {
        return dataBrokerTransporter.subscribeById(signalIds)
    }

    /**
     * Subscribe to a set of signals using string path parameters
     * Returns (GRPC error code):
     *    NOT_FOUND if any of the signals are non-existent.
     *    PERMISSION_DENIED if access is denied for any of the signals.
     *
     * When subscribing the Broker shall immediately return the value for all
     * subscribed entries. If no value is available when subscribing a DataPoint
     * with value None shall be returned.
     */
    fun subscribe(
        signalPaths: List<String>,
    ): Flow<KuksaValV2.SubscribeResponse> {
        return dataBrokerTransporter.subscribe(signalPaths)
    }

    /**
     * Actuates a single actuator with the specified [signalId].
     *
     * The server might respond with the following GRPC error codes:
     *    NOT_FOUND if the actuator does not exist.
     *    PERMISSION_DENIED if access is denied for of the actuator.
     *    UNAVAILABLE if there is no provider currently providing the actuator
     *    INVALID_ARGUMENT
     *        - if the data type used in the request does not match the data type of the addressed signal
     *        - if the requested value is not accepted, e.g. if sending an unsupported enum value
     */
    suspend fun actuate(signalId: Types.SignalID, value: Value): KuksaValV2.ActuateResponse {
        return dataBrokerTransporter.actuate(signalId, value)
    }

    /**
     * Actuates simultaneously multiple actuators with the specified [signalIds].
     * If any error occurs, the entire operation will be aborted and no single actuator value will be forwarded to the
     * provider.
     *
     * The server might respond with the following GRPC error codes:
     *     NOT_FOUND if any of the actuators are non-existent.
     *     PERMISSION_DENIED if access is denied for any of the actuators.
     *     UNAVAILABLE if there is no provider currently providing an actuator
     *     INVALID_ARGUMENT
     *         - if the data type used in the request does not match the data type of the addressed signal
     *         - if the requested value is not accepted, e.g. if sending an unsupported enum value
     *
     */
    suspend fun batchActuate(signalIds: List<Types.SignalID>, value: Value): KuksaValV2.BatchActuateResponse {
        return dataBrokerTransporter.batchActuate(signalIds, value)
    }

    /**
     * Lists metadata of signals matching the request.
     * If any error occurs, the entire operation will be aborted and no single actuator value will be forwarded to the
     * provider.
     *
     * The server might respond with the following GRPC error codes:
     *     NOT_FOUND if the specified root branch does not exist.
     */
    suspend fun listMetadata(
        root: String,
        filter: String,
    ): KuksaValV2.ListMetadataResponse {
        return dataBrokerTransporter.listMetadata(root, filter)
    }

    /**
     * Publishes a signal value. Used for low frequency signals (e.g. attributes).
     *
     * The server might respond with the following GRPC error codes:
     *     NOT_FOUND if any of the signals are non-existent.
     *     PERMISSION_DENIED
     *         - if access is denied for any of the signals.
     *         - if the signal is already provided by another provider.
     *     INVALID_ARGUMENT
     *        - if the data type used in the request does not match the data type of the addressed signal
     *        - if the published value is not accepted e.g. if sending an unsupported enum value
     */
    suspend fun publishValue(
        signalId: Types.SignalID,
        datapoint: Types.Datapoint,
    ): KuksaValV2.PublishValueResponse {
        return dataBrokerTransporter.publishValue(signalId, datapoint)
    }

    /**
     *  Open a stream used to provide actuation and/or publishing values using
     *  a streaming interface. Used to provide actuators and to enable high frequency
     *  updates of values.
     *
     *  The open stream is used for request / response type communication between the
     *  provider and server (where the initiator of a request can vary).
     *  Errors are communicated as messages in the stream.
     */
    fun openProviderStream(
        streamRequestFlow: Flow<KuksaValV2.OpenProviderStreamRequest>,
    ): Flow<KuksaValV2.OpenProviderStreamResponse> {
        return dataBrokerTransporter.openProviderStream(streamRequestFlow)
    }

    /**
     * Gets the server information.
     */
    suspend fun fetchServerInfo(): KuksaValV2.GetServerInfoResponse {
        return dataBrokerTransporter.fetchServerInfo()
    }
}
