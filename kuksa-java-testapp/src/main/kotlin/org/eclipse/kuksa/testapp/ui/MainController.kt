package org.eclipse.kuksa.testapp.ui

import javafx.scene.Parent
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.eclipse.kuksa.testapp.model.ConnectionState
import org.eclipse.kuksa.testapp.viewmodel.DataBrokerViewModel

class MainController(
    val viewModel: DataBrokerViewModel,
    private val stage: Stage? = null,
    private val uiScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
) {
    val connectionController = ConnectionController(viewModel, uiScope)
    val signalController = SignalController(viewModel, uiScope)
    val actuatorController = ActuatorController(viewModel, uiScope)
    val subscriptionController = SubscriptionController(viewModel, uiScope)
    val logController = LogController(viewModel, uiScope)

    val tabPane = TabPane()

    val connectionTab = Tab("Connection", connectionController.view).apply { isClosable = false }
    val sensorsTab = Tab("Sensors", signalController.view).apply { isClosable = false }
    val actuatorsTab = Tab("Actuators", actuatorController.view).apply { isClosable = false }
    val subscriptionsTab = Tab("Subscriptions", subscriptionController.view).apply { isClosable = false }
    val logTab = Tab("Log", logController.view).apply { isClosable = false }

    val root: Parent get() = tabPane

    init {
        tabPane.tabs.addAll(connectionTab, sensorsTab, actuatorsTab, subscriptionsTab, logTab)

        // Disable operational tabs initially
        setOperationalTabsDisabled(true)

        uiScope.launch {
            viewModel.connectionState.collect { state ->
                val isConnected = (state == ConnectionState.CONNECTED)
                setOperationalTabsDisabled(!isConnected)
                stage?.title = "Kuksa Java TestApp - [$state]"
            }
        }
    }

    private fun setOperationalTabsDisabled(disabled: Boolean) {
        sensorsTab.isDisable = disabled
        actuatorsTab.isDisable = disabled
        subscriptionsTab.isDisable = disabled
        logTab.isDisable = disabled
    }
}
