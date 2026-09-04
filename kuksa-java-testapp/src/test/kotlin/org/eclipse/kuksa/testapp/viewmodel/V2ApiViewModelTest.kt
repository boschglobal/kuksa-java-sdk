package org.eclipse.kuksa.testapp.viewmodel

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.eclipse.kuksa.connectivity.databroker.DataBrokerConnection
import org.eclipse.kuksa.connectivity.databroker.v2.KuksaValV2Protocol
import org.eclipse.kuksa.connectivity.databroker.v2.request.ActuateRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.FetchValueRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.PublishValueRequestV2
import org.eclipse.kuksa.connectivity.databroker.v2.request.SubscribeRequestV2
import org.eclipse.kuksa.proto.v2.KuksaValV2
import org.eclipse.kuksa.proto.v2.Types
import org.eclipse.kuksa.testapp.model.ConnectionConfig

@OptIn(ExperimentalCoroutinesApi::class)
class V2ApiViewModelTest : BehaviorSpec({
    val testDispatcher = StandardTestDispatcher()
    val testScope = TestScope(testDispatcher)

    Given("A DataBrokerViewModel connected with mocked V2 protocol") {
        val viewModel = DataBrokerViewModel(scope = testScope)
        val mockConnection = mockk<DataBrokerConnection>(relaxed = true)
        val mockV2 = mockk<KuksaValV2Protocol>(relaxed = true)
        every { mockConnection.kuksaValV2 } returns mockV2

        viewModel.setConnectedStateForTesting(mockConnection, ConnectionConfig())

        val signalId = Types.SignalID.newBuilder().setPath("Vehicle.Speed").build()

        When("Invoking fetchValueV2") {
            val response = KuksaValV2.GetValueResponse.newBuilder().build()
            coEvery { mockV2.fetchValue(any<FetchValueRequestV2>()) } returns response

            viewModel.fetchValueV2(signalId)
            testScope.advanceUntilIdle()

            Then("Request and Response logs are recorded") {
                viewModel.logEntries.value shouldHaveSize 2
            }
        }

        When("Invoking publishValueV2") {
            viewModel.clearLog()
            val response = KuksaValV2.PublishValueResponse.newBuilder().build()
            coEvery { mockV2.publishValue(any<PublishValueRequestV2>()) } returns response

            val dp = Types.Datapoint.newBuilder().setValue(Types.Value.newBuilder().setFloat(50.0f)).build()
            viewModel.publishValueV2(signalId, dp)
            testScope.advanceUntilIdle()

            Then("Request and Response logs are recorded") {
                viewModel.logEntries.value shouldHaveSize 2
            }
        }

        When("Invoking actuateV2") {
            viewModel.clearLog()
            val response = KuksaValV2.ActuateResponse.newBuilder().build()
            coEvery { mockV2.actuate(any<ActuateRequestV2>()) } returns response

            val valObj = Types.Value.newBuilder().setFloat(120.0f).build()
            viewModel.actuateV2(signalId, valObj)
            testScope.advanceUntilIdle()

            Then("Request and Response logs are recorded") {
                viewModel.logEntries.value shouldHaveSize 2
            }
        }

        When("Invoking subscribeByPathV2") {
            viewModel.clearLog()
            val response = KuksaValV2.SubscribeResponse.newBuilder().build()
            every { mockV2.subscribe(any<SubscribeRequestV2>()) } returns flowOf(response)

            viewModel.subscribeByPathV2(listOf("Vehicle.Speed"))
            testScope.advanceUntilIdle()

            Then("Subscription is active") {
                viewModel.activeSubscriptions.value.containsKey("v2-path-Vehicle.Speed") shouldBe true
            }
        }

        When("Invoking fetchServerInfoV2") {
            viewModel.clearLog()
            val response = KuksaValV2.GetServerInfoResponse.newBuilder()
                .setName("KuksaMock")
                .setVersion("0.4.0")
                .build()
            coEvery { mockV2.fetchServerInfo() } returns response

            viewModel.fetchServerInfoV2()
            testScope.advanceUntilIdle()

            Then("Server info is logged") {
                viewModel.logEntries.value shouldHaveSize 2
            }
        }
    }
})
