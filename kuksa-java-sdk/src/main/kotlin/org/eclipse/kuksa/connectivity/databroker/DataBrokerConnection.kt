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

package org.eclipse.kuksa.connectivity.databroker

import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.eclipse.kuksa.connectivity.authentication.JsonWebToken
import org.eclipse.kuksa.connectivity.databroker.v1.DataBrokerTransporterV1
import org.eclipse.kuksa.connectivity.databroker.v1.KuksaValV1Protocol
import org.eclipse.kuksa.connectivity.databroker.v2.DataBrokerTransporterV2
import org.eclipse.kuksa.connectivity.databroker.v2.KuksaValV2Protocol
import org.eclipse.kuksa.extension.TAG
import org.eclipse.kuksa.pattern.listener.MultiListener
import java.util.logging.Logger
import kotlin.properties.Delegates

/**
 * The DataBrokerConnection holds an active connection to the DataBroker. The Connection can be use to interact with the
 * DataBroker.
 */
class DataBrokerConnection internal constructor(
    private val managedChannel: ManagedChannel,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val dataBrokerTransporterV1: DataBrokerTransporterV1 = DataBrokerTransporterV1(
        managedChannel,
    ),
    private val dataBrokerTransporterV2: DataBrokerTransporterV2 = DataBrokerTransporterV2(
        managedChannel,
    ),
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
        dataBrokerTransporterV1.jsonWebToken = newValue
        dataBrokerTransporterV2.jsonWebToken = newValue
    }

    val kuksaValV1 = KuksaValV1Protocol(managedChannel, dataBrokerTransporterV1, dispatcher)
    val kuksaValV2 = KuksaValV2Protocol(managedChannel, dataBrokerTransporterV2)

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
     * Disconnect from the DataBroker.
     */
    fun disconnect() {
        logger.finer("disconnect() called")
        managedChannel.shutdownNow()
    }
}
