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

package com.example.samples

import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.eclipse.kuksa.connectivity.databroker.v1.DataBrokerConnector
import org.eclipse.kuksa.connectivity.databroker.v1.listener.VssPathListener
import org.eclipse.kuksa.connectivity.databroker.v1.request.FetchRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.SubscribeRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.UpdateRequest
import org.eclipse.kuksa.connectivity.databroker.v2.DataBrokerConnectorV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.FetchValueRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.PublishValueRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.SubscribeRequestV2
import org.eclipse.kuksa.proto.v1.KuksaValV1
import org.eclipse.kuksa.proto.v1.Types.Datapoint
import org.eclipse.kuksa.proto.v2.Types
import org.eclipse.kuksa.proto.v2.Types.SignalID

suspend fun main() {
    useKuksaValV1()
    useKuksaValV2()
}

@Suppress("MagicNumber")
private suspend fun useKuksaValV1() {
    val managedChannel = ManagedChannelBuilder.forAddress("localhost", 55556)
        .usePlaintext()
        .build()
    val dataBrokerConnector = DataBrokerConnector(managedChannel)

    coroutineScope {
        launch {
            val dataBrokerConnection = dataBrokerConnector.connect()

            println("Using protocol kuksa.val.v1")
            println("Setting Vehicle.Speed in Databroker to 80")
            val vssPath = "Vehicle.Speed"

            val dataPoint = Datapoint.newBuilder().setFloat(80.0F).build()
            val updateRequest = UpdateRequest(vssPath, dataPoint)
            dataBrokerConnection.update(updateRequest)

            println("Reading Vehicle.Speed from Databroker")
            val fetchRequest = FetchRequest(vssPath)
            val response = dataBrokerConnection.fetch(fetchRequest)
            println("GetResponse: " + response)

            println("Observe Vehicle.Speed")
            val subscribeRequest = SubscribeRequest("Vehicle.Speed")
            dataBrokerConnection.subscribe(
                subscribeRequest,
                object : VssPathListener {
                    override fun onEntryChanged(entryUpdates: List<KuksaValV1.EntryUpdate>) {
                        entryUpdates.forEach { entryUpdate ->
                            val vehicleSpeedValue = entryUpdate.entry.value.float
                            // handle change
                            println("newSpeed(v1): $vehicleSpeedValue")
                        }
                    }

                    override fun onError(throwable: Throwable) {
                        // handle error
                    }
                },
            )
        }
    }
}

@Suppress("MagicNumber")
private suspend fun useKuksaValV2() {
    val managedChannel = ManagedChannelBuilder.forAddress("localhost", 55556)
        .usePlaintext()
        .build()
    val dataBrokerConnector = DataBrokerConnectorV2(managedChannel)

    coroutineScope {
        launch {
            val dataBrokerConnection = dataBrokerConnector.connect()

            println("Using protocol kuksa.val.v2")
            println("Setting Vehicle.Speed in Databroker to 60")

            val signalId = SignalID.newBuilder().setPath("Vehicle.Speed").build()
            val speedValue = Types.Value.newBuilder().setFloat(60.0F).build()
            val datapoint = Types.Datapoint.newBuilder().setValue(speedValue).build()

            val publishValueRequest = PublishValueRequestV2(signalId, datapoint)
            dataBrokerConnection.publishValue(publishValueRequest)

            println("Reading Vehicle.Speed from Databroker")
            val fetchValueRequest = FetchValueRequestV2(signalId)
            val fetchResponse = dataBrokerConnection.fetchValue(fetchValueRequest)
            println(fetchResponse)

            println("Observe Vehicle.Speed")
            val signalPaths = listOf("Vehicle.Speed")
            val subscribeRequest = SubscribeRequestV2(signalPaths)
            val responseFlow = dataBrokerConnection.subscribe(subscribeRequest)
            responseFlow.collect { response ->
                val vehicleSpeedValue = response.entriesMap["Vehicle.Speed"]?.value?.float
                // handle change
                println("newSpeed(v2): $vehicleSpeedValue")
            }
        }
    }
}
