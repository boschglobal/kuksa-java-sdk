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

import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.flow.Flow
import org.eclipse.kuksa.connectivity.authentication.JsonWebToken
import org.eclipse.kuksa.connectivity.databroker.DataBrokerException
import org.eclipse.kuksa.connectivity.databroker.v2.request.ActuateRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.BatchActuateRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.FetchValueRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.FetchValuesRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.ListMetadataRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.PublishValueRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.SubscribeByIdRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.SubscribeRequestV2
import org.eclipse.kuksa.proto.v2.KuksaValV2
import kotlin.properties.Delegates

/**
 * The DataBrokerConnection holds an active connection to the DataBroker. The Connection can be use to interact with the
 * DataBroker.
 */
@Suppress("TooManyFunctions") // most methods are simply exposed from transporter layer
class KuksaValV2Protocol internal constructor(
    private val managedChannel: ManagedChannel,
    private val dataBrokerTransporterV2: DataBrokerTransporterV2 = DataBrokerTransporterV2(managedChannel),
) {
    /**
     * A JsonWebToken can be provided to authenticate against the DataBroker.
     */
    var jsonWebToken: JsonWebToken? by Delegates.observable(null) { _, _, newValue ->
        dataBrokerTransporterV2.jsonWebToken = newValue
    }

    /**
     * Gets the latest value of a [signalId].
     *
     * The server might respond with the following GRPC error codes:
     *    NOT_FOUND if the requested signal doesn't exist
     *    PERMISSION_DENIED if access is denied
     *
     * @throws DataBrokerException when an error occurs
     */
    suspend fun fetchValue(request: FetchValueRequestV2): KuksaValV2.GetValueResponse {
        return dataBrokerTransporterV2.fetchValue(request.signalId)
    }

    /**
     * Gets the latest values of a set of [signalIds]. The returned list of data points has the same order as the list
     * of the request.
     *
     * The server might respond with the following GRPC error codes:
     *    NOT_FOUND if any of the requested signals doesn't exist.
     *    PERMISSION_DENIED if access is denied for any of the requested signals.
     *
     * @throws DataBrokerException when an error occurs
     *
     */
    suspend fun fetchValues(request: FetchValuesRequestV2): KuksaValV2.GetValuesResponse {
        return dataBrokerTransporterV2.fetchValues(request.signalIds)
    }

    /**
     * Subscribe to a set of signals using i32 id parameters
     * Returns (GRPC error code):
     *    NOT_FOUND if any of the signals are non-existent.
     *    PERMISSION_DENIED if access is denied for any of the signals.
     *
     * @throws DataBrokerException when an error occurs
     */
    fun subscribeById(
        request: SubscribeByIdRequestV2,
    ): Flow<KuksaValV2.SubscribeByIdResponse> {
        return dataBrokerTransporterV2.subscribeById(request.signalIds)
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
     *
     * @throws DataBrokerException when an error occurs
     */
    fun subscribe(
        request: SubscribeRequestV2,
    ): Flow<KuksaValV2.SubscribeResponse> {
        return dataBrokerTransporterV2.subscribe(request.signalPaths)
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
     *
     * @throws DataBrokerException when an error occurs
     */
    suspend fun actuate(request: ActuateRequestV2): KuksaValV2.ActuateResponse {
        return dataBrokerTransporterV2.actuate(request.signalId, request.value)
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
     * @throws DataBrokerException when an error occurs
     */
    suspend fun batchActuate(request: BatchActuateRequestV2): KuksaValV2.BatchActuateResponse {
        return dataBrokerTransporterV2.batchActuate(request.signalIds, request.value)
    }

    /**
     * Lists metadata of signals matching the request.
     * If any error occurs, the entire operation will be aborted and no single actuator value will be forwarded to the
     * provider.
     *
     * The server might respond with the following GRPC error codes:
     *     NOT_FOUND if the specified root branch does not exist.
     *
     * @throws DataBrokerException when an error occurs
     */
    suspend fun listMetadata(request: ListMetadataRequestV2): KuksaValV2.ListMetadataResponse {
        return dataBrokerTransporterV2.listMetadata(request.root, request.filter)
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
     *
     * @throws DataBrokerException when an error occurs
     */
    suspend fun publishValue(
        request: PublishValueRequestV2,
    ): KuksaValV2.PublishValueResponse {
        return dataBrokerTransporterV2.publishValue(request.signalId, request.datapoint)
    }

    /**
     *  Open a stream used to provide actuation and/or publishing values using
     *  a streaming interface. Used to provide actuators and to enable high frequency
     *  updates of values.
     *
     *  The open stream is used for request / response type communication between the
     *  provider and server (where the initiator of a request can vary).
     *  Errors are communicated as messages in the stream.
     *
     * @throws DataBrokerException when an error occurs
     */
    fun openProviderStream(
        responseStream: StreamObserver<KuksaValV2.OpenProviderStreamResponse>,
    ): StreamObserver<KuksaValV2.OpenProviderStreamRequest> {
        return dataBrokerTransporterV2.openProviderStream(responseStream)
    }

    /**
     * Gets the server information.
     *
     * @throws DataBrokerException when an error occurs
     */
    suspend fun fetchServerInfo(): KuksaValV2.GetServerInfoResponse {
        return dataBrokerTransporterV2.fetchServerInfo()
    }
}
