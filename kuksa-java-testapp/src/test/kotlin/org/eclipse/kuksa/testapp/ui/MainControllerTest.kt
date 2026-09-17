package org.eclipse.kuksa.testapp.ui

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.eclipse.kuksa.testapp.model.ConnectionConfig
import org.eclipse.kuksa.testapp.viewmodel.DataBrokerViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class MainControllerTest : BehaviorSpec({
    val testDispatcher = StandardTestDispatcher()
    val testScope = TestScope(testDispatcher)

    Given("A MainController") {
        try {
            javafx.application.Platform.startup {}
        } catch (_: IllegalStateException) {
        }

        val viewModel = DataBrokerViewModel(scope = testScope)
        val mainController = MainController(viewModel, null, testScope)

        When("Initially disconnected") {
            Then("Connection tab is enabled, other tabs are disabled") {
                mainController.connectionTab.isDisable shouldBe false
                mainController.sensorsTab.isDisable shouldBe true
                mainController.actuatorsTab.isDisable shouldBe true
                mainController.subscriptionsTab.isDisable shouldBe true
                mainController.logTab.isDisable shouldBe true
            }
        }

        When("State transitions to CONNECTED") {
            viewModel.setConnectedStateForTesting(mockk(relaxed = true), ConnectionConfig())
            testScope.advanceUntilIdle()

            Then("All operational tabs become enabled") {
                mainController.sensorsTab.isDisable shouldBe false
                mainController.actuatorsTab.isDisable shouldBe false
                mainController.subscriptionsTab.isDisable shouldBe false
                mainController.logTab.isDisable shouldBe false
            }
        }
    }
})
