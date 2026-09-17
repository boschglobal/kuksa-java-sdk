package org.eclipse.kuksa.testapp.ui

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.eclipse.kuksa.testapp.model.ConnectionConfig
import org.eclipse.kuksa.testapp.model.ConnectionState
import org.eclipse.kuksa.testapp.viewmodel.DataBrokerViewModel

// UI layout spacing values are self-documenting in context; named constants would add noise.
@Suppress("MagicNumber")
class ConnectionController(
    private val viewModel: DataBrokerViewModel,
    private val uiScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
) {
    val hostField = TextField("localhost")
    val portField = TextField("55556")
    val jwtField = TextField()
    val tlsCheckBox = CheckBox("Use TLS")

    val connectButton = Button("Connect")
    val disconnectButton = Button("Disconnect")
    val statusLabel = Label("Status: DISCONNECTED")
    val errorBanner = Label()

    val view: Node by lazy { buildView() }

    init {
        errorBanner.isWrapText = true
        errorBanner.style = "-fx-text-fill: red; -fx-font-weight: bold;"
        errorBanner.isVisible = false

        connectButton.setOnAction {
            val port = portField.text.trim().toIntOrNull() ?: 0
            val config = ConnectionConfig(
                host = hostField.text.trim(),
                port = port,
                jwt = jwtField.text.takeIf { it.isNotBlank() },
                useTls = tlsCheckBox.isSelected,
            )
            viewModel.connect(config)
        }

        disconnectButton.setOnAction {
            viewModel.disconnect()
        }

        uiScope.launch {
            viewModel.connectionState.collect { state ->
                updateState(state)
            }
        }
    }

    private fun updateState(state: ConnectionState) {
        statusLabel.text = "Status: $state"
        when (state) {
            ConnectionState.DISCONNECTED -> {
                connectButton.isDisable = false
                disconnectButton.isDisable = true
                errorBanner.isVisible = false
            }
            ConnectionState.CONNECTING -> {
                connectButton.isDisable = true
                disconnectButton.isDisable = true
                errorBanner.isVisible = false
            }
            ConnectionState.CONNECTED -> {
                connectButton.isDisable = true
                disconnectButton.isDisable = false
                errorBanner.isVisible = false
            }
            ConnectionState.ERROR -> {
                connectButton.isDisable = false
                disconnectButton.isDisable = true
                val lastError = viewModel.logEntries.value.lastOrNull {
                    it.direction == org.eclipse.kuksa.testapp.model.Direction.ERROR
                }
                errorBanner.text = lastError?.payload ?: "Connection error"
                errorBanner.isVisible = true
            }
        }
    }

    private fun buildView(): Node {
        val root = VBox(15.0)
        root.padding = Insets(20.0)
        root.alignment = Pos.TOP_LEFT

        val grid = GridPane()
        grid.hgap = 10.0
        grid.vgap = 10.0

        grid.add(Label("Host:"), 0, 0)
        grid.add(hostField, 1, 0)

        grid.add(Label("Port:"), 0, 1)
        grid.add(portField, 1, 1)

        grid.add(Label("JWT (Optional):"), 0, 2)
        grid.add(jwtField, 1, 2)

        grid.add(tlsCheckBox, 1, 3)

        val btnBox = HBox(15.0, connectButton, disconnectButton)
        disconnectButton.isDisable = true

        root.children.addAll(
            Label("DataBroker Connection Settings").apply { style = "-fx-font-size: 16px; -fx-font-weight: bold;" },
            grid,
            btnBox,
            statusLabel.apply { style = "-fx-font-size: 14px;" },
            errorBanner,
        )

        return root
    }
}
