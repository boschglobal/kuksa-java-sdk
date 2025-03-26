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

package org.eclipse.kuksa.connectivity.databroker.v2.authentication

import io.grpc.StatusException
import io.grpc.stub.StreamObserver
import io.kotest.assertions.fail
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.instanceOf
import kotlinx.coroutines.flow.first
import org.eclipse.kuksa.connectivity.databroker.DataBrokerException
import org.eclipse.kuksa.connectivity.databroker.docker.DataBrokerDockerContainer
import org.eclipse.kuksa.connectivity.databroker.docker.SecureDataBrokerDockerContainer
import org.eclipse.kuksa.connectivity.databroker.provider.DataBrokerConnectorProvider
import org.eclipse.kuksa.connectivity.databroker.v2.request.ActuateRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.FetchValueRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.PublishValueRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.SubscribeRequestV2
import org.eclipse.kuksa.mocking.JwtType
import org.eclipse.kuksa.proto.v2.KuksaValV2.OpenProviderStreamRequest
import org.eclipse.kuksa.proto.v2.KuksaValV2.OpenProviderStreamResponse
import org.eclipse.kuksa.proto.v2.KuksaValV2.ProvideActuationRequest
import org.eclipse.kuksa.proto.v2.Types.Datapoint
import org.eclipse.kuksa.proto.v2.Types.SignalID
import org.eclipse.kuksa.proto.v2.Types.Value
import org.eclipse.kuksa.test.kotest.Authentication
import org.eclipse.kuksa.test.kotest.Integration
import org.eclipse.kuksa.test.kotest.Secure
import org.eclipse.kuksa.test.kotest.SecureDataBroker
import kotlin.random.Random
import kotlin.random.nextInt

// DataBroker must be started with Authentication enabled:
// databroker --jwt-public-key /certs/jwt/jwt.key.pub

// ./gradlew clean test -Dkotest.tags="Authentication"
class DataBrokerConnectorAuthenticationTest : BehaviorSpec({
    tags(Integration, Authentication, Secure, SecureDataBroker)

    var databrokerContainer: DataBrokerDockerContainer? = null
    beforeSpec {
        databrokerContainer = SecureDataBrokerDockerContainer()
            .apply {
                start()
            }
    }

    afterSpec {
        databrokerContainer?.stop()
    }

    val random = Random(System.nanoTime())

    given("A DataBrokerConnectorProvider") {
        val dataBrokerConnectorProvider = DataBrokerConnectorProvider()
        val speedVssPath = "Vehicle.Speed"

        and("a secure DataBrokerConnector with a READ_WRITE_ALL JWT") {
            val jwtFile = JwtType.READ_WRITE_ALL

            val dataBrokerConnector = dataBrokerConnectorProvider.createSecure(
                port = databrokerContainer!!.port,
                jwtFileStream = jwtFile.asInputStream(),
            )

            and("a successfully established connection") {
                val connection = dataBrokerConnector.connect()

                `when`("Reading Vehicle.Speed") {
                    val fetchRequest = FetchValueRequestV2.fromVssPath(speedVssPath)
                    val result = runCatching { connection.kuksaValV2.fetchValue(fetchRequest) }

                    then("No error should occur") {
                        result.onSuccess { response ->
                            response.hasDataPoint() shouldBe true
                        }.onFailure {
                            fail("Reading Vehicle.Speed should not fail")
                        }
                    }
                }

                `when`("Writing the VALUE of Vehicle.Speed") {
                    val nextFloat = random.nextFloat() * 100F

                    val value = Value.newBuilder().setFloat(nextFloat).build()
                    val datapoint = Datapoint.newBuilder().setValue(value).build()
                    val publishValueRequest = PublishValueRequestV2.fromVssPath(speedVssPath, datapoint)

                    val result = runCatching {
                        connection.kuksaValV2.publishValue(publishValueRequest)
                    }

                    then("No error should occur") {
                        result.onFailure { e ->
                            e shouldBe instanceOf(DataBrokerException::class)
                            fail("Writing Vehicle.Speed should not fail")
                        }
                    }
                }
            }
        }

        and("a secure DataBrokerConnector with a READ_ALL JWT") {
            val jwtFile = JwtType.READ_ALL
            val dataBrokerConnector = dataBrokerConnectorProvider.createSecure(
                port = databrokerContainer!!.port,
                jwtFileStream = jwtFile.asInputStream(),
            )

            and("a successfully established connection") {
                val connection = dataBrokerConnector.connect()

                `when`("Reading Vehicle.Speed") {
                    val fetchRequest = FetchValueRequestV2.fromVssPath("Vehicle.Speed")
                    val result = runCatching { connection.kuksaValV2.fetchValue(fetchRequest) }

                    then("No error should occur") {
                        result.onSuccess { response ->
                            response.hasDataPoint() shouldBe true
                        }.onFailure {
                            fail("Reading Vehicle.Speed should not fail")
                        }
                    }
                }

                `when`("Writing the VALUE of Vehicle.Speed") {
                    val nextFloat = random.nextFloat() * 100F

                    val value = Value.newBuilder().setFloat(nextFloat).build()
                    val datapoint = Datapoint.newBuilder().setValue(value).build()
                    val publishValueRequest = PublishValueRequestV2.fromVssPath("Vehicle.Speed", datapoint)

                    val result = runCatching {
                        connection.kuksaValV2.publishValue(publishValueRequest)
                    }

                    then("An error should occur") {
                        result.onSuccess {
                            fail("Writing Vehicle.Speed with a READ_ALL JWT should not succeed")
                        }.onFailure { e ->
                            e shouldBe instanceOf(DataBrokerException::class)
                        }
                    }
                }
            }
        }

        and("a secure DataBrokerConnector with a READ_WRITE_ALL_VALUES_ONLY JWT") {
            val jwtFile = JwtType.READ_WRITE_ALL_VALUES_ONLY
            val dataBrokerConnector = dataBrokerConnectorProvider.createSecure(
                port = databrokerContainer!!.port,
                jwtFileStream = jwtFile.asInputStream(),
            )

            and("a successfully established connection") {
                val connection = dataBrokerConnector.connect()

                val panVssPath = "Vehicle.Body.Mirrors.DriverSide.Pan"

                `when`("Reading the ACTUATOR_TARGET of Vehicle.Body.Mirrors.DriverSide.Pan") {
                    val fetchRequest = FetchValueRequestV2.fromVssPath(panVssPath)
                    val result = runCatching { connection.kuksaValV2.fetchValue(fetchRequest) }

                    then("No error should occur") {
                        result.onSuccess { response ->
                            response.hasDataPoint() shouldBe true
                        }.onFailure {
                            fail("Reading Vehicle.Speed should not fail")
                        }
                    }
                }

                `when`("Trying to actuate an actuator of Vehicle.Body.Mirrors.DriverSide.Pan") {
                    val nextInt = random.nextInt(-100..100)

                    val openProviderStream =
                        connection.kuksaValV2.openProviderStream(object : StreamObserver<OpenProviderStreamResponse> {
                            override fun onNext(value: OpenProviderStreamResponse?) {
                                // ignored
                            }

                            override fun onError(t: Throwable?) {
                                // ignored
                            }

                            override fun onCompleted() {
                                // ignored
                            }
                        })

                    val signalId = SignalID.newBuilder().setPath(panVssPath).build()
                    val addActuatorIdentifiers =
                        ProvideActuationRequest.newBuilder().addActuatorIdentifiers(signalId).build()
                    val provideActuationRequest = OpenProviderStreamRequest.newBuilder()
                        .setProvideActuationRequest(addActuatorIdentifiers).build()
                    openProviderStream.onNext(provideActuationRequest)

                    val value = Value.newBuilder().setInt32(nextInt).build()
                    val actuateRequest = ActuateRequestV2.fromVssPath(panVssPath, value)

                    val result = runCatching {
                        connection.kuksaValV2.actuate(actuateRequest)
                    }

                    then("An error should occur") {
                        result.onSuccess {
                            fail("Writing an actuator ($panVssPath) with a READ_WRITE_ALL_VALUES_ONLY JWT should fail")
                        }.onFailure { e ->
                            e shouldBe instanceOf(DataBrokerException::class)
                        }
                    }
                }

                `when`("Reading the VALUE of Vehicle.Speed") {
                    val fetchRequest = FetchValueRequestV2.fromVssPath("Vehicle.Speed")
                    val result = runCatching { connection.kuksaValV2.fetchValue(fetchRequest) }

                    then("No error should occur") {
                        result.onSuccess { response ->
                            response.hasDataPoint() shouldBe true
                        }.onFailure {
                            fail("Reading Vehicle.Speed with a READ_WRITE_ALL_VALUES_ONLY JWT should not fail")
                        }
                    }
                }

                `when`("Writing the VALUE of Vehicle.Speed") {
                    val nextFloat = random.nextFloat() * 100F

                    val value = Value.newBuilder().setFloat(nextFloat).build()
                    val datapoint = Datapoint.newBuilder().setValue(value).build()
                    val publishValueRequest = PublishValueRequestV2.fromVssPath("Vehicle.Speed", datapoint)

                    val result = runCatching {
                        connection.kuksaValV2.publishValue(publishValueRequest)
                    }

                    then("No error should occur") {
                        result.onFailure {
                            fail("Writing Vehicle.Speed with a READ_WRITE_ALL_VALUES_ONLY JWT should not fail")
                        }
                    }
                }
            }
        }

        and("a secure DataBrokerConnector with no JWT") {
            val dataBrokerConnector = dataBrokerConnectorProvider.createSecure(
                port = databrokerContainer!!.port,
                jwtFileStream = null,
            )

            `when`("Trying to connect") {
                val connectionResult = runCatching {
                    dataBrokerConnector.connect()
                }

                then("The connection should be successful") {
                    connectionResult.getOrNull() shouldNotBe null
                }

                val connection = connectionResult.getOrNull()!!

                `when`("Reading the VALUE of Vehicle.Speed") {
                    val fetchRequest = FetchValueRequestV2.fromVssPath("Vehicle.Speed")
                    val result = runCatching { connection.kuksaValV2.fetchValue(fetchRequest) }

                    then("An error should occur") {
                        result.onSuccess {
                            fail("Reading Vehicle.Speed without a JWT should not succeed")
                        }.onFailure { e ->
                            e shouldBe instanceOf(DataBrokerException::class)
                        }
                    }
                }

                `when`("Writing Vehicle.Speed") {
                    val nextFloat = random.nextFloat() * 100F

                    val value = Value.newBuilder().setFloat(nextFloat).build()
                    val datapoint = Datapoint.newBuilder().setValue(value).build()
                    val publishValueRequest = PublishValueRequestV2.fromVssPath("Vehicle.Speed", datapoint)

                    val result = runCatching {
                        connection.kuksaValV2.publishValue(publishValueRequest)
                    }

                    then("An error should occur") {
                        result.onSuccess {
                            fail("Writing Vehicle.Speed without a JWT should not succeed")
                        }.onFailure { e ->
                            e shouldBe instanceOf(DataBrokerException::class)
                        }
                    }
                }

                `when`("Subscribing to Vehicle.Speed and consuming the stream") {
                    val subscribeRequest = SubscribeRequestV2(listOf("Vehicle.Speed"))
                    val flow = connection.kuksaValV2.subscribe(subscribeRequest)

                    val result = runCatching {
                        flow.first()
                    }

                    then("An error should occur") {
                        result.onSuccess {
                            fail("Subscribing Vehicle.Speed without a JWT should not succeed")
                        }.onFailure { e ->
                            e shouldBe instanceOf(StatusException::class)
                        }
                    }
                }
            }
        }
    }
})
