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
import org.eclipse.kuksa.connectivity.databroker.v1.KuksaValV1Protocol
import org.eclipse.kuksa.connectivity.databroker.v1.request.FetchRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.SubscribeRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.UpdateRequest
import org.eclipse.kuksa.connectivity.databroker.v1.request.VssNodeUpdateRequest
import org.eclipse.kuksa.connectivity.databroker.v1.response.VssNodeUpdateResponse
import org.eclipse.kuksa.proto.v1.KuksaValV1
import org.eclipse.kuksa.proto.v1.Types
import org.eclipse.kuksa.testapp.model.ConnectionConfig

@OptIn(ExperimentalCoroutinesApi::class)
class V1ApiViewModelTest : BehaviorSpec({
    val testDispatcher = StandardTestDispatcher()
    val testScope = TestScope(testDispatcher)

    Given("A DataBrokerViewModel connected with mocked V1 protocol") {
        val viewModel = DataBrokerViewModel(scope = testScope)
        val mockConnection = mockk<DataBrokerConnection>(relaxed = true)
        val mockV1 = mockk<KuksaValV1Protocol>(relaxed = true)
        every { mockConnection.kuksaValV1 } returns mockV1

        viewModel.setConnectedStateForTesting(mockConnection, ConnectionConfig())

        When("Invoking fetchV1") {
            val response = KuksaValV1.GetResponse.newBuilder().build()
            coEvery { mockV1.fetch(any<FetchRequest>()) } returns response

            viewModel.fetchV1("Vehicle.Speed", Types.Field.FIELD_VALUE)
            testScope.advanceUntilIdle()

            Then("Request and Response logs are recorded") {
                viewModel.logEntries.value shouldHaveSize 2
            }
        }

        When("Invoking updateV1") {
            viewModel.clearLog()
            val setResponse = KuksaValV1.SetResponse.newBuilder().build()
            coEvery { mockV1.update(any<UpdateRequest>()) } returns setResponse

            val dp = Types.Datapoint.newBuilder().setFloat(100.0f).build()
            viewModel.updateV1("Vehicle.Speed", dp, Types.Field.FIELD_VALUE)
            testScope.advanceUntilIdle()

            Then("Request and Response logs are recorded") {
                viewModel.logEntries.value shouldHaveSize 2
            }
        }

        When("Invoking updateNodeV1") {
            viewModel.clearLog()
            val setResponse = mockk<VssNodeUpdateResponse>(relaxed = true)
            coEvery { mockV1.update(any<VssNodeUpdateRequest<*>>()) } returns setResponse

            val testSignal = object : org.eclipse.kuksa.vsscore.model.VssSignal<Float> {
                override val uuid: String = "uuid-123"
                override val vssPath: String = "Vehicle.Speed"
                override val description: String = "Vehicle speed"
                override val type: String = "sensor"
                override val comment: String = ""
                override val value: Float = 100.0f
            }
            viewModel.updateNodeV1(testSignal)
            testScope.advanceUntilIdle()

            Then("Request and Response logs are recorded with node value") {
                viewModel.logEntries.value shouldHaveSize 2
                viewModel.logEntries.value.first().payload shouldBe "node: Vehicle.Speed (value: 100.0)"
            }
        }

        When("Invoking subscribeV1Flow") {
            viewModel.clearLog()
            val subResponse = KuksaValV1.SubscribeResponse.newBuilder().build()
            every { mockV1.subscribe(any<SubscribeRequest>()) } returns flowOf(subResponse)

            viewModel.subscribeV1Flow("Vehicle.Speed", Types.Field.FIELD_VALUE)
            testScope.advanceUntilIdle()

            Then("Subscription is stored and active") {
                viewModel.activeSubscriptions.value.containsKey("v1-flow-Vehicle.Speed") shouldBe true
            }
        }
    }
})
