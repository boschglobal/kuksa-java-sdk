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
     * Get the latest value of a signal
     * If the signal exist but does not have a valid value
     * a DataPoint where value is None shall be returned.
     *
     * Returns (GRPC error code):
     *   NOT_FOUND if the requested signal doesn't exist
     *   UNAUTHENTICATED if no credentials provided or credentials has expired
     *   PERMISSION_DENIED if access is denied
     *   INVALID_ARGUMENT if the request is empty or provided path is too long
     *       - MAX_REQUEST_PATH_LENGTH: usize = 1000;
     *
     * @throws DataBrokerException when an error occurs
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
     * Get the latest values of a set of signals.
     * The returned list of data points has the same order as the list of the request.
     * If a requested signal has no value a DataPoint where value is None will be returned.
     *
     * Returns (GRPC error code):
     *   NOT_FOUND if any of the requested signals doesn't exist.
     *   UNAUTHENTICATED if no credentials provided or credentials has expired
     *   PERMISSION_DENIED if access is denied for any of the requested signals.
     *   INVALID_ARGUMENT if the request is empty or provided path is too long
     *       - MAX_REQUEST_PATH_LENGTH: usize = 1000;
     *
     * @throws DataBrokerException when an error occurs
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
     *   NOT_FOUND if any of the signals are non-existant.
     *   UNAUTHENTICATED if no credentials provided or credentials has expired
     *   PERMISSION_DENIED if access is denied for any of the signals.
     *   INVALID_ARGUMENT
     *       - if the request is empty or provided path is too long
     *             MAX_REQUEST_PATH_LENGTH: usize = 1000;
     *       - if buffer_size exceeds the maximum permitted
     *             MAX_BUFFER_SIZE: usize = 1000;
     *
     * When subscribing, Databroker shall immediately return the value for all
     * subscribed entries.
     * If a value isn't available when subscribing to a it, it should return None
     *
     * If a subscriber is slow to consume signals, messages will be buffered up
     * to the specified buffer_size before the oldest messages are dropped.
     *
     * @throws DataBrokerException when an error occurs
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
     *   NOT_FOUND if any of the signals are non-existant.
     *   UNAUTHENTICATED if no credentials provided or credentials has expired
     *   PERMISSION_DENIED if access is denied for any of the signals.
     *   INVALID_ARGUMENT
     *       - if the request is empty or provided path is too long
     *             MAX_REQUEST_PATH_LENGTH: usize = 1000;
     *       - if buffer_size exceeds the maximum permitted
     *             MAX_BUFFER_SIZE: usize = 1000;
     *
     * When subscribing, Databroker shall immediately return the value for all
     * subscribed entries.
     * If a value isn't available when subscribing to a it, it should return None
     *
     * If a subscriber is slow to consume signals, messages will be buffered up
     * to the specified buffer_size before the oldest messages are dropped.
     *
     * @throws DataBrokerException when an error occurs
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
     * Actuate a single actuator
     *
     * Returns (GRPC error code):
     *   NOT_FOUND if the actuator does not exist.
     *   PERMISSION_DENIED if access is denied for the actuator.
     *   UNAUTHENTICATED if no credentials provided or credentials has expired
     *   UNAVAILABLE if there is no provider currently providing the actuator
     *   DATA_LOSS is there is a internal TransmissionFailure
     *   INVALID_ARGUMENT
     *       - if the provided path is not an actuator.
     *       - if the data type used in the request does not match
     *            the data type of the addressed signal
     *       - if the requested value is not accepted,
     *            e.g. if sending an unsupported enum value
     *       - if the provided value is out of the min/max range specified
     *
     * @throws DataBrokerException when an error occurs
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
     * Actuate simultaneously multiple actuators.
     * If any error occurs, the entire operation will be aborted
     * and no single actuator value will be forwarded to the provider.
     *
     * Returns (GRPC error code):
     *   NOT_FOUND if any of the actuators are non-existant.
     *   PERMISSION_DENIED if access is denied for any of the actuators.
     *   UNAUTHENTICATED if no credentials provided or credentials has expired
     *   UNAVAILABLE if there is no provider currently providing an actuator
     *   DATA_LOSS is there is a internal TransmissionFailure
     *   INVALID_ARGUMENT
     *       - if any of the provided path is not an actuator.
     *       - if the data type used in the request does not match
     *            the data type of the addressed signal
     *       - if the requested value is not accepted,
     *            e.g. if sending an unsupported enum value
     *       - if any of the provided actuators values are out of the min/max range specified
     *
     * @throws DataBrokerException when an error occurs
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
     * List metadata of signals matching the request.
     *
     * Returns (GRPC error code):
     *   NOT_FOUND if the specified root branch does not exist.
     *   UNAUTHENTICATED if no credentials provided or credentials has expired
     *   INVALID_ARGUMENT if the provided path or wildcard is wrong.
     *
     * @throws DataBrokerException when an error occurs
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
     * Publish a signal value. Used for low frequency signals (e.g. attributes).
     *
     * Returns (GRPC error code):
     *   NOT_FOUND if any of the signals are non-existant.
     *   PERMISSION_DENIED
     *       - if access is denied for any of the signals.
     *   UNAUTHENTICATED if no credentials provided or credentials has expired
     *   INVALID_ARGUMENT
     *       - if the data type used in the request does not match
     *            the data type of the addressed signal
     *       - if the published value is not accepted,
     *            e.g. if sending an unsupported enum value
     *       - if the published value is out of the min/max range specified
     *
     * @throws DataBrokerException when an error occurs
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
     * Open a stream used to provide actuation and/or publishing values using
     * a streaming interface. Used to provide actuators and to enable high frequency
     * updates of values.
     *
     * The open stream is used for request / response type communication between the
     * provider and server (where the initiator of a request can vary).
     *
     * Errors:
     *    - Provider sends ProvideActuationRequest -> Databroker returns ProvideActuationResponse
     *        Returns (GRPC error code) and closes the stream call (strict case).
     *          NOT_FOUND if any of the signals are non-existant.
     *          PERMISSION_DENIED if access is denied for any of the signals.
     *          UNAUTHENTICATED if no credentials provided or credentials has expired
     *          ALREADY_EXISTS if a provider already claimed the ownership of an actuator
     *
     *    - Provider sends PublishValuesRequest -> Databroker returns PublishValuesResponse upon error,
     *      and nothing upon success
     *        GRPC errors are returned as messages in the stream
     *        response with the signal id `map<int32, Error> status = 2;` (permissive case)
     *          NOT_FOUND if a signal is non-existant.
     *          PERMISSION_DENIED
     *              - if access is denied for a signal.
     *          INVALID_ARGUMENT
     *              - if the data type used in the request does not match
     *                   the data type of the addressed signal
     *              - if the published value is not accepted,
     *                   e.g. if sending an unsupported enum value
     *              - if the published value is out of the min/max range specified
     *
     *    - Databroker sends BatchActuateStreamRequest -> Provider shall return a BatchActuateStreamResponse,
     *        for every signal requested to indicate if the request was accepted or not.
     *        It is up to the provider to decide if the stream shall be closed,
     *        as of today Databroker will not react on the received error message.
     *
     * @throws DataBrokerException when an error occurs
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
     *
     * @throws DataBrokerException when an error occurs
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
