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
import io.grpc.StatusException
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.flow.Flow
import org.eclipse.kuksa.connectivity.authentication.JsonWebToken
import org.eclipse.kuksa.connectivity.databroker.DataBrokerException
import org.eclipse.kuksa.connectivity.databroker.v2.extension.withAuthenticationInterceptor
import org.eclipse.kuksa.proto.v2.KuksaValV2
import org.eclipse.kuksa.proto.v2.Types
import org.eclipse.kuksa.proto.v2.Types.Value
import org.eclipse.kuksa.proto.v2.VALGrpc
import org.eclipse.kuksa.proto.v2.VALGrpcKt
import org.eclipse.kuksa.proto.v2.actuateRequest
import org.eclipse.kuksa.proto.v2.batchActuateRequest
import org.eclipse.kuksa.proto.v2.getServerInfoRequest
import org.eclipse.kuksa.proto.v2.getValueRequest
import org.eclipse.kuksa.proto.v2.getValuesRequest
import org.eclipse.kuksa.proto.v2.listMetadataRequest
import org.eclipse.kuksa.proto.v2.publishValueRequest
import org.eclipse.kuksa.proto.v2.subscribeByIdRequest
import org.eclipse.kuksa.proto.v2.subscribeRequest

/**
 * Encapsulates the Protobuf-specific interactions with the DataBroker send over gRPC.
 * The DataBrokerTransporter requires a [managedChannel] which is already connected to the corresponding DataBroker.
 *
 * @throws IllegalStateException in case the state of the [managedChannel] is not [ConnectivityState.READY]
 */
internal class DataBrokerTransporterV2(
    private val managedChannel: ManagedChannel,
) {

    init {
        val state = managedChannel.getState(false)
        check(state == ConnectivityState.READY) {
            "ManagedChannel needs to be connected to the target"
        }
    }

    /**
     * A JsonWebToken can be provided to authenticate against the DataBroker.
     */
    var jsonWebToken: JsonWebToken? = null

    private val coroutineStub: VALGrpcKt.VALCoroutineStub = VALGrpcKt.VALCoroutineStub(managedChannel)
    private val asyncStub: VALGrpc.VALStub = VALGrpc.newStub(managedChannel)

    /**
     * Gets the latest value of a [signalId].
     *
     * The server might respond with the following GRPC error codes:
     *    NOT_FOUND if the requested signal doesn't exist
     *    PERMISSION_DENIED if access is denied
     */
    suspend fun fetchValue(signalId: Types.SignalID): KuksaValV2.GetValueResponse {
        val request = getValueRequest {
            this.signalId = signalId
        }

        return try {
            coroutineStub
                .withAuthenticationInterceptor(jsonWebToken)
                .getValue(request)
        } catch (e: StatusException) {
            throw DataBrokerException(e.message, e)
        }
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
        val request = getValuesRequest {
            this.signalIds.addAll(signalIds)
        }

        return try {
            coroutineStub
                .withAuthenticationInterceptor(jsonWebToken)
                .getValues(request)
        } catch (e: StatusException) {
            throw DataBrokerException(e.message, e)
        }
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
        val request = subscribeByIdRequest {
            this.signalIds.addAll(signalIds)
        }

        return try {
            coroutineStub
                .withAuthenticationInterceptor(jsonWebToken)
                .subscribeById(request)
        } catch (e: StatusException) {
            throw DataBrokerException(e.message, e)
        }
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
        val request = subscribeRequest {
            this.signalPaths.addAll(signalPaths)
        }

        return try {
            coroutineStub
                .withAuthenticationInterceptor(jsonWebToken)
                .subscribe(request)
        } catch (e: StatusException) {
            throw DataBrokerException(e.message, e)
        }
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
        val request = actuateRequest {
            this.signalId = signalId
            this.value = value
        }

        return try {
            coroutineStub
                .withAuthenticationInterceptor(jsonWebToken)
                .actuate(request)
        } catch (e: StatusException) {
            throw DataBrokerException(e.message, e)
        }
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
        val request = batchActuateRequest {
            signalIds.forEach { signalId ->
                val actuateRequest = actuateRequest {
                    this.signalId = signalId
                    this.value = value
                }
                this.actuateRequests.add(actuateRequest)
            }
        }

        return try {
            coroutineStub
                .withAuthenticationInterceptor(jsonWebToken)
                .batchActuate(request)
        } catch (e: StatusException) {
            throw DataBrokerException(e.message, e)
        }
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
        val request = listMetadataRequest {
            this.root = root
            this.filter = filter
        }

        return try {
            coroutineStub
                .withAuthenticationInterceptor(jsonWebToken)
                .listMetadata(request)
        } catch (e: StatusException) {
            throw DataBrokerException(e.message, e)
        }
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
        val request = publishValueRequest {
            this.signalId = signalId
            this.dataPoint = datapoint
        }

        return try {
            coroutineStub
                .withAuthenticationInterceptor(jsonWebToken)
                .publishValue(request)
        } catch (e: StatusException) {
            throw DataBrokerException(e.message, e)
        }
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
        responseStream: StreamObserver<KuksaValV2.OpenProviderStreamResponse>,
    ): StreamObserver<KuksaValV2.OpenProviderStreamRequest> {
        return try {
            asyncStub
                .withAuthenticationInterceptor(jsonWebToken)
                .openProviderStream(responseStream)
        } catch (e: StatusException) {
            throw DataBrokerException(e.message, e)
        }
    }

    /**
     * Gets the server information.
     */
    suspend fun fetchServerInfo(): KuksaValV2.GetServerInfoResponse {
        val request = getServerInfoRequest { }

        return try {
            coroutineStub
                .withAuthenticationInterceptor(jsonWebToken)
                .getServerInfo(request)
        } catch (e: StatusException) {
            throw DataBrokerException(e.message, e)
        }
    }
}
