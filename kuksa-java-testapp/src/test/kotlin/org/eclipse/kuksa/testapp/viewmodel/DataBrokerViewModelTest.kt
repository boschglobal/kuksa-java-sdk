package org.eclipse.kuksa.testapp.viewmodel

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.eclipse.kuksa.connectivity.databroker.DataBrokerConnection
import org.eclipse.kuksa.testapp.model.ConnectionConfig
import org.eclipse.kuksa.testapp.model.ConnectionState
import org.eclipse.kuksa.testapp.model.Direction

@OptIn(ExperimentalCoroutinesApi::class)
class DataBrokerViewModelTest : BehaviorSpec({
    val testDispatcher = StandardTestDispatcher()
    val testScope = TestScope(testDispatcher)

    Given("A DataBrokerViewModel") {
        val viewModel = DataBrokerViewModel(scope = testScope)

        When("Initial state") {
            Then("It is DISCONNECTED with empty logs and subscriptions") {
                viewModel.connectionState.value shouldBe ConnectionState.DISCONNECTED
                viewModel.logEntries.value.shouldBeEmpty()
                viewModel.activeSubscriptions.value.shouldBeEmpty()
            }
        }

        When("Connecting with invalid config") {
            val invalidConfig = ConnectionConfig(host = "", port = 0)
            viewModel.connect(invalidConfig)
            testScope.advanceUntilIdle()

            Then("State transitions to ERROR and error log is appended") {
                viewModel.connectionState.value shouldBe ConnectionState.ERROR
                viewModel.logEntries.value shouldHaveSize 1
                viewModel.logEntries.value.first().direction shouldBe Direction.ERROR
            }
        }

        When("Clearing the log") {
            viewModel.clearLog()

            Then("Log entries are empty") {
                viewModel.logEntries.value.shouldBeEmpty()
            }
        }

        When("Simulating manual disconnect on connected connection") {
            val mockConnection = mockk<DataBrokerConnection>(relaxed = true)
            viewModel.connection = mockConnection
            viewModel.setConnectedStateForTesting(mockConnection, ConnectionConfig())

            viewModel.connectionState.value shouldBe ConnectionState.CONNECTED

            viewModel.disconnect()
            testScope.advanceUntilIdle()

            Then("State transitions to DISCONNECTED") {
                viewModel.connectionState.value shouldBe ConnectionState.DISCONNECTED
            }
        }
    }
})
