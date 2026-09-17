package org.eclipse.kuksa.testapp.ui

import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.SplitPane
import javafx.scene.control.TextArea
import javafx.scene.control.TextInputDialog
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.eclipse.kuksa.testapp.model.Direction
import org.eclipse.kuksa.testapp.viewmodel.DataBrokerViewModel

// UI layout spacing values are self-documenting in context; named constants would add noise.
@Suppress("MagicNumber")
class SubscriptionController(
    private val viewModel: DataBrokerViewModel,
    private val uiScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
) {
    val subscriptionsList = FXCollections.observableArrayList<String>()
    val subscriptionListView = ListView(subscriptionsList)

    val addByPathBtn = Button("+ Subscribe by Path")
    val addByIdBtn = Button("+ Subscribe by ID")
    val unsubscribeBtn = Button("Unsubscribe")

    val liveEventLogArea = TextArea().apply {
        isEditable = false
        style = "-fx-font-family: monospace; -fx-font-size: 11px;"
    }

    val view: Node by lazy { buildView() }

    init {
        wireActions()

        uiScope.launch {
            viewModel.activeSubscriptions.collect { subs ->
                subscriptionsList.setAll(subs.keys)
            }
        }

        uiScope.launch {
            viewModel.logEntries.collect { entries ->
                val events = entries.filter { it.direction == Direction.EVENT }
                val text = events.takeLast(100).joinToString("\n") { it.format() }
                liveEventLogArea.text = text
                liveEventLogArea.scrollTop = Double.MAX_VALUE
            }
        }
    }

    private fun wireActions() {
        addByPathBtn.setOnAction {
            val dialog = TextInputDialog("Vehicle.Speed").apply {
                title = "Subscribe by Path"
                headerText = "Enter VSS Path to subscribe (v2):"
                contentText = "Path:"
            }
            dialog.showAndWait().ifPresent { path ->
                if (path.isNotBlank()) {
                    viewModel.subscribeByPathV2(listOf(path.trim()))
                }
            }
        }

        addByIdBtn.setOnAction {
            val dialog = TextInputDialog("100").apply {
                title = "Subscribe by ID"
                headerText = "Enter integer Signal ID to subscribe (v2):"
                contentText = "Signal ID:"
            }
            dialog.showAndWait().ifPresent { idStr ->
                val id = idStr.trim().toIntOrNull()
                if (id != null) {
                    viewModel.subscribeByIdV2(listOf(id))
                }
            }
        }

        unsubscribeBtn.setOnAction {
            val selected = subscriptionListView.selectionModel.selectedItem
            if (selected != null) {
                viewModel.unsubscribe(selected)
            }
        }
    }

    private fun buildView(): Node {
        val root = VBox(10.0)
        root.padding = Insets(10.0)

        val btnBox = HBox(8.0, addByPathBtn, addByIdBtn, unsubscribeBtn)

        val leftPane = VBox(
            8.0,
            Label("Active Subscriptions:").apply { style = "-fx-font-weight: bold;" },
            subscriptionListView.apply { VBox.setVgrow(this, Priority.ALWAYS) },
            btnBox,
        )

        val rightPane = VBox(
            8.0,
            Label("Live Event Stream:").apply { style = "-fx-font-weight: bold;" },
            liveEventLogArea.apply { VBox.setVgrow(this, Priority.ALWAYS) },
        )

        val splitPane = SplitPane(leftPane, rightPane).apply {
            setDividerPositions(0.35)
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        root.children.add(splitPane)
        VBox.setVgrow(splitPane, Priority.ALWAYS)
        return root
    }
}
