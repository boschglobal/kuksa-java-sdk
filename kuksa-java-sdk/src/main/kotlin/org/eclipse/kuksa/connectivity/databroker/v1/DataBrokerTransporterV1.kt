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

import io.grpc.ConnectivityState
import io.grpc.Context
import io.grpc.ManagedChannel
import io.grpc.StatusException
import io.grpc.stub.StreamObserver
import org.eclipse.kuksa.connectivity.authentication.JsonWebToken
import org.eclipse.kuksa.connectivity.databroker.DataBrokerException
import org.eclipse.kuksa.connectivity.databroker.v1.extension.withAuthenticationInterceptor
import org.eclipse.kuksa.connectivity.databroker.v1.listener.VssPathListener
import org.eclipse.kuksa.extension.TAG
import org.eclipse.kuksa.extension.applyDatapoint
import org.eclipse.kuksa.proto.v1.KuksaValV1.*
import org.eclipse.kuksa.proto.v1.Types
import org.eclipse.kuksa.proto.v1.Types.Field
import org.eclipse.kuksa.proto.v1.VALGrpc
import org.eclipse.kuksa.proto.v1.VALGrpcKt
import java.util.logging.Logger

/**
 * Encapsulates the Protobuf-specific interactions with the DataBroker send over gRPC. Provides fetch, update and
 * subscribe methods to retrieve and update data, as well as registering to be notified about external data updates
 * using a Subscription.
 * The DataBrokerTransporter requires a [managedChannel] which is already connected to the corresponding DataBroker.
 *
 * @throws IllegalStateException in case the state of the [managedChannel] is not [ConnectivityState.READY]
 */
internal class DataBrokerTransporterV1(
    private val managedChannel: ManagedChannel,
) {

    private val logger = Logger.getLogger(TAG)

    init {
        val state = managedChannel.getState(false)
        check(state == ConnectivityState.READY) {
            "ManagedChannel needs to be connected to the target"
        }
    }

    private val coroutineStub = VALGrpcKt.VALCoroutineStub(managedChannel)
    private val asyncStub = VALGrpc.newStub(managedChannel)

    /**
     * A JsonWebToken can be provided to authenticate against the DataBroker.
     */
    var jsonWebToken: JsonWebToken? = null

    /**
     * Sends a request to the DataBroker to respond with the specified [vssPath] and [fields] values.
     *
     * @throws DataBrokerException in case the connection to the DataBroker is no longer active
     */
    suspend fun fetch(
        vssPath: String,
        fields: Collection<Field>,
    ): GetResponse {
        val entryRequest = EntryRequest.newBuilder()
            .setPath(vssPath)
            .addAllFields(fields.toSet())
            .build()
        val request = GetRequest.newBuilder()
            .addEntries(entryRequest)
            .build()

        return try {
            coroutineStub
                .withAuthenticationInterceptor(jsonWebToken)
                .get(request)
        } catch (e: StatusException) {
            throw DataBrokerException(e.message, e)
        }
    }

    /**
     * Sends a request to the DataBroker to update the specified [fields] of the [vssPath] and replace it's value with
     * the specified [updatedDatapoint].
     *
     * @throws DataBrokerException in case the connection to the DataBroker is no longer active
     */
    suspend fun update(
        vssPath: String,
        updatedDatapoint: Types.Datapoint,
        fields: Collection<Field>,
    ): SetResponse {
        val entryUpdates = fields.map { field ->
            val dataEntry = Types.DataEntry.newBuilder()
                .setPath(vssPath)
                .applyDatapoint(updatedDatapoint, field)
                .build()

            EntryUpdate.newBuilder()
                .setEntry(dataEntry)
                .addFields(field)
                .build()
        }

        val request = SetRequest.newBuilder()
            .addAllUpdates(entryUpdates)
            .build()

        return try {
            coroutineStub
                .withAuthenticationInterceptor(jsonWebToken)
                .set(request)
        } catch (e: StatusException) {
            throw DataBrokerException(e.message, e)
        }
    }

    /**
     * Sends a request to the DataBroker to subscribe to updates of the specified [vssPath] and [fields].
     * Returns a [Context.CancellableContext] which can be used to cancel the subscription.
     *
     * @throws DataBrokerException in case the connection to the DataBroker is no longer active
     */
    fun subscribe(
        vssPath: String,
        fields: List<Field>,
        vssPathListener: VssPathListener,
    ): Context.CancellableContext {
        val subscribeEntry = SubscribeEntry.newBuilder()
            .setPath(vssPath)
            .addAllFields(fields)
            .build()

        val request = SubscribeRequest.newBuilder()
            .addEntries(subscribeEntry)
            .build()

        val currentContext = Context.current()
        val cancellableContext = currentContext.withCancellation()

        val streamObserver = object : StreamObserver<SubscribeResponse> {
            override fun onNext(value: SubscribeResponse) {
                vssPathListener.onEntryChanged(value.updatesList)
            }

            override fun onError(throwable: Throwable) {
                vssPathListener.onError(throwable)
            }

            override fun onCompleted() {
                logger.finer("onCompleted() called")
            }
        }

        cancellableContext.run {
            try {
                asyncStub
                    .withAuthenticationInterceptor(jsonWebToken)
                    .subscribe(request, streamObserver)
            } catch (e: StatusException) {
                throw DataBrokerException(e.message, e)
            }
        }

        return cancellableContext
    }
}
