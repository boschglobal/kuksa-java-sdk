package org.eclipse.kuksa.testapp.ui

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.eclipse.kuksa.testapp.viewmodel.DataBrokerViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionControllerTest : BehaviorSpec({
    val testDispatcher = StandardTestDispatcher()
    val testScope = TestScope(testDispatcher)

    Given("A ConnectionController with mocked ViewModel") {
        // Initializing JavaFX toolkit for tests
        try {
            javafx.application.Platform.startup {}
        } catch (_: IllegalStateException) {
            // Already started
        }

        val viewModel = DataBrokerViewModel(scope = testScope)
        val controller = ConnectionController(viewModel, testScope)
        testScope.advanceUntilIdle()

        When("Initial state (UI-1)") {
            Then("Connect is enabled, Disconnect is disabled") {
                controller.connectButton.isDisable shouldBe false
                controller.disconnectButton.isDisable shouldBe true
                controller.statusLabel.text shouldBe "Status: DISCONNECTED"
            }
        }

        When("ViewModel transitions to CONNECTED (UI-2)") {
            viewModel.setConnectedStateForTesting(
                mockk(relaxed = true),
                org.eclipse.kuksa.testapp.model.ConnectionConfig(),
            )
            testScope.advanceUntilIdle()

            Then("Connect is disabled, Disconnect is enabled") {
                controller.connectButton.isDisable shouldBe true
                controller.disconnectButton.isDisable shouldBe false
                controller.statusLabel.text shouldBe "Status: CONNECTED"
            }
        }

        When("ViewModel transitions to ERROR (UI-3)") {
            val invalid = org.eclipse.kuksa.testapp.model.ConnectionConfig(host = "", port = 0)
            viewModel.connect(invalid)
            testScope.advanceUntilIdle()

            Then("Error banner is visible and Connect is enabled for retry") {
                controller.connectButton.isDisable shouldBe false
                controller.errorBanner.isVisible shouldBe true
            }
        }
    }
})
