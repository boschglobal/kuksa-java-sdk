package org.eclipse.kuksa.testapp.ui

import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.eclipse.kuksa.testapp.viewmodel.DataBrokerViewModel

// UI layout spacing values are self-documenting in context; named constants would add noise.
@Suppress("MagicNumber")
class LogController(
    private val viewModel: DataBrokerViewModel,
    private val uiScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
) {
    val logTextArea = TextArea().apply {
        isEditable = false
        style = "-fx-font-family: monospace; -fx-font-size: 11px;"
    }

    val clearBtn = Button("Clear Log")
    val serverInfoBtn = Button("Server Info (v2)")
    val exportBtn = Button("Export Log")

    val view: Node by lazy { buildView() }

    init {
        clearBtn.setOnAction {
            viewModel.clearLog()
        }

        serverInfoBtn.setOnAction {
            viewModel.fetchServerInfoV2()
        }

        exportBtn.setOnAction {
            val fileChooser = FileChooser().apply {
                title = "Save Log"
                initialFileName = "kuksa-testapp-log.txt"
            }
            val file = fileChooser.showSaveDialog(null)
            if (file != null) {
                file.writeText(logTextArea.text)
            }
        }

        uiScope.launch {
            viewModel.logEntries.collect { entries ->
                val fullLog = entries.joinToString("\n") { it.format() }
                logTextArea.text = fullLog
                logTextArea.scrollTop = Double.MAX_VALUE
            }
        }
    }

    private fun buildView(): Node {
        val root = VBox(10.0)
        root.padding = Insets(10.0)

        val toolbar = HBox(10.0, clearBtn, serverInfoBtn, exportBtn)

        root.children.addAll(
            Label("Audit & Operation Log").apply { style = "-fx-font-size: 14px; -fx-font-weight: bold;" },
            toolbar,
            logTextArea.apply { VBox.setVgrow(this, Priority.ALWAYS) },
        )
        VBox.setVgrow(root, Priority.ALWAYS)
        return root
    }
}
