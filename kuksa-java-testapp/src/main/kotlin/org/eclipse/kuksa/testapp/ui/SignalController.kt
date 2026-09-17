/*
 * Copyright (c) 2023 - 2026 Contributors to the Eclipse Foundation
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

package org.eclipse.kuksa.testapp.ui

import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.ScrollPane
import javafx.scene.control.SplitPane
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.ToggleButton
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.eclipse.kuksa.extension.vss.copy
import org.eclipse.kuksa.testapp.domain.DataTypeSerializer
import org.eclipse.kuksa.testapp.domain.PathHistory
import org.eclipse.kuksa.testapp.model.Direction
import org.eclipse.kuksa.testapp.model.LogEntry
import org.eclipse.kuksa.testapp.model.SignalItem
import org.eclipse.kuksa.testapp.repository.VssSignalRepository
import org.eclipse.kuksa.testapp.viewmodel.DataBrokerViewModel
import org.eclipse.kuksa.vsscore.model.VssSignal
import org.eclipse.kuksa.proto.v1.Types as V1Types
import org.eclipse.kuksa.proto.v2.Types as V2Types

// UI layout spacing and proportions are self-documenting in context; named constants would add noise.
@Suppress("MagicNumber")
class SignalController(
    private val viewModel: DataBrokerViewModel,
    private val uiScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
) {
    val isVssModelMode = ToggleButton("VSS Model").apply { isSelected = true }
    val isRawPathMode = ToggleButton("Raw Path")

    val searchFilterField = TextField().apply { promptText = "Filter signals..." }
    val pathHistory = PathHistory(20)

    val rawPathField = TextField("Vehicle.Speed").apply { promptText = "e.g. Vehicle.Speed" }
    val rawHistoryCombo = ComboBox<String>()
    val rawDataTypeCombo = ComboBox(
        FXCollections.observableArrayList("float", "boolean", "int32", "uint32", "int64", "uint64", "double", "string"),
    ).apply { selectionModel.select("float") }

    private val allSignalsList = FXCollections.observableArrayList<SignalItem>()
    private val filteredSignals = FilteredList(allSignalsList) { true }
    val signalListView = ListView(filteredSignals)

    val currentPathLabel = Label("-").apply { style = "-fx-font-weight: bold;" }
    val currentTypeLabel = Label("-")
    val currentDataTypeLabel = Label("-")

    val valueInputField = TextField().apply { promptText = "Enter value..." }

    // v1 buttons
    val fetchV1PathBtn = Button("Fetch (Path)")
    val fetchV1NodeBtn = Button("Fetch (Node)")
    val updateV1PathBtn = Button("Update (Path)")
    val updateV1NodeBtn = Button("Update (Node)")
    val subV1ListenerBtn = Button("Subscribe (Listener)")
    val subV1FlowBtn = Button("Subscribe (Flow)")
    val subV1NodeBtn = Button("Subscribe (Node)")

    // v2 buttons
    val fetchV2ValueBtn = Button("Fetch Value")
    val fetchV2BulkBtn = Button("Fetch All / Bulk")
    val publishV2ValueBtn = Button("Publish Value")
    val subV2PathBtn = Button("Subscribe (Path)")
    val listMetadataV2Btn = Button("Metadata")

    val miniLogArea = TextArea().apply {
        isEditable = false
        maxHeight = Double.MAX_VALUE
        style = "-fx-font-family: monospace; -fx-font-size: 11px;"
    }

    private var selectedSignal: SignalItem? = null
    val view: Node by lazy { buildView() }

    init {
        allSignalsList.setAll(VssSignalRepository.sensors())

        signalListView.setCellFactory {
            object : ListCell<SignalItem>() {
                override fun updateItem(item: SignalItem?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) null else "[${item.signalType.uppercase()}] ${item.vssPath}"
                }
            }
        }

        signalListView.selectionModel.selectedItemProperty().addListener { _, _, newSelection ->
            selectedSignal = newSelection
            newSelection?.let {
                currentPathLabel.text = it.vssPath
                currentTypeLabel.text = it.signalType
                currentDataTypeLabel.text = it.dataType
            }
        }

        searchFilterField.textProperty().addListener { _, _, text ->
            filteredSignals.setPredicate { item ->
                text.isNullOrBlank() || item.vssPath.contains(text, ignoreCase = true) || item.signalType.contains(
                    text,
                    ignoreCase = true,
                )
            }
        }

        wireActions()

        uiScope.launch {
            viewModel.logEntries.collect { entries ->
                val recent = entries.takeLast(10).joinToString("\n") { it.format() }
                miniLogArea.text = recent
                miniLogArea.scrollTop = Double.MAX_VALUE
            }
        }
    }

    private fun getActivePath(): String {
        return if (isVssModelMode.isSelected) {
            selectedSignal?.vssPath ?: "Vehicle.Speed"
        } else {
            val path = rawPathField.text.trim()
            pathHistory.record(path)
            rawHistoryCombo.items = FXCollections.observableArrayList(pathHistory.toList())
            path
        }
    }

    private fun getActiveDataType(): String {
        return if (isVssModelMode.isSelected) {
            selectedSignal?.dataType ?: "float"
        } else {
            rawDataTypeCombo.value ?: "float"
        }
    }

    // Each action handler is a one-liner; grouping them here keeps wire-up logic in one place.
    // Exception caught at the UI boundary where the originating type from the copy() extension is unknown.
    @Suppress("LongMethod", "TooGenericExceptionCaught")
    private fun wireActions() {
        fetchV1PathBtn.setOnAction {
            viewModel.fetchV1(getActivePath(), V1Types.Field.FIELD_VALUE)
        }
        fetchV1NodeBtn.setOnAction {
            selectedSignal?.vssNode?.let { viewModel.fetchNodeV1(it) }
        }
        updateV1PathBtn.setOnAction {
            val dp = DataTypeSerializer.toDatapoint(getActiveDataType(), valueInputField.text.trim())
            viewModel.updateV1(getActivePath(), dp, V1Types.Field.FIELD_VALUE)
        }
        updateV1NodeBtn.setOnAction {
            val node = selectedSignal?.vssNode ?: return@setOnAction
            try {
                val dp = DataTypeSerializer.toDatapoint(getActiveDataType(), valueInputField.text.trim())
                val updatedNode = if (node is VssSignal<*>) {
                    node.copy(dp)
                } else {
                    node.copy(node.vssPath, dp)
                }
                viewModel.updateNodeV1(updatedNode)
            } catch (e: Exception) {
                viewModel.appendLog(
                    LogEntry(
                        direction = Direction.ERROR,
                        apiMethod = "v1.updateNode",
                        payload = "Failed to copy node value: ${e.message}",
                    ),
                )
            }
        }
        subV1ListenerBtn.setOnAction {
            viewModel.subscribeV1Listener(getActivePath(), V1Types.Field.FIELD_VALUE)
        }
        subV1FlowBtn.setOnAction {
            viewModel.subscribeV1Flow(getActivePath(), V1Types.Field.FIELD_VALUE)
        }
        subV1NodeBtn.setOnAction {
            selectedSignal?.vssNode?.let { viewModel.subscribeNodeV1(it) }
        }

        fetchV2ValueBtn.setOnAction {
            val signalId = V2Types.SignalID.newBuilder().setPath(getActivePath()).build()
            viewModel.fetchValueV2(signalId)
        }
        fetchV2BulkBtn.setOnAction {
            val ids = allSignalsList.take(20).map {
                V2Types.SignalID.newBuilder().setPath(it.vssPath).build()
            }
            viewModel.fetchValuesV2(ids)
        }
        publishV2ValueBtn.setOnAction {
            val signalId = V2Types.SignalID.newBuilder().setPath(getActivePath()).build()
            val valObj = DataTypeSerializer.toValue(getActiveDataType(), valueInputField.text.trim())
            val dp = V2Types.Datapoint.newBuilder().setValue(valObj).build()
            viewModel.publishValueV2(signalId, dp)
        }
        subV2PathBtn.setOnAction {
            viewModel.subscribeByPathV2(listOf(getActivePath()))
        }
        listMetadataV2Btn.setOnAction {
            viewModel.listMetadataV2(root = getActivePath())
        }

        rawHistoryCombo.setOnAction {
            rawHistoryCombo.value?.let { rawPathField.text = it }
        }
    }

    // JavaFX scene graphs are inherently declarative and verbose; extracting sub-methods would fragment readability.
    @Suppress("LongMethod", "MagicNumber")
    private fun buildView(): Node {
        val root = VBox(10.0)
        root.padding = Insets(10.0)

        val modeBar = HBox(15.0, Label("Input Mode:"), isVssModelMode, isRawPathMode)
        modeBar.alignment = javafx.geometry.Pos.CENTER_LEFT

        val vssModelLeftPane = VBox(8.0, searchFilterField, signalListView).apply {
            VBox.setVgrow(signalListView, Priority.ALWAYS)
        }

        val rawPathLeftPane = VBox(
            8.0,
            Label("VSS Path:"),
            rawPathField,
            Label("Data Type:"),
            rawDataTypeCombo,
            Label("History:"),
            rawHistoryCombo,
        )

        val leftContainer = StackPane(vssModelLeftPane, rawPathLeftPane)
        rawPathLeftPane.isVisible = false

        isVssModelMode.setOnAction {
            vssModelLeftPane.isVisible = true
            rawPathLeftPane.isVisible = false
            fetchV1NodeBtn.isVisible = true
            updateV1NodeBtn.isVisible = true
            subV1NodeBtn.isVisible = true
        }
        isRawPathMode.setOnAction {
            vssModelLeftPane.isVisible = false
            rawPathLeftPane.isVisible = true
            fetchV1NodeBtn.isVisible = false
            updateV1NodeBtn.isVisible = false
            subV1NodeBtn.isVisible = false
        }

        val detailGrid = GridPane().apply {
            hgap = 10.0
            vgap = 8.0
            add(Label("Path:"), 0, 0)
            add(currentPathLabel, 1, 0)
            add(Label("Type:"), 0, 1)
            add(currentTypeLabel, 1, 1)
            add(Label("Data Type:"), 0, 2)
            add(currentDataTypeLabel, 1, 2)
            add(Label("Value:"), 0, 3)
            add(valueInputField, 1, 3)
        }

        val v1ButtonsBox = VBox(
            5.0,
            Label("V1 Operations:").apply { style = "-fx-font-weight: bold;" },
            HBox(8.0, fetchV1PathBtn, fetchV1NodeBtn, updateV1PathBtn, updateV1NodeBtn),
            HBox(8.0, subV1ListenerBtn, subV1FlowBtn, subV1NodeBtn),
        )

        val v2ButtonsBox = VBox(
            5.0,
            Label("V2 Operations:").apply { style = "-fx-font-weight: bold;" },
            HBox(8.0, fetchV2ValueBtn, fetchV2BulkBtn, publishV2ValueBtn),
            HBox(8.0, subV2PathBtn, listMetadataV2Btn),
        )

        val rightPane = VBox(
            12.0,
            detailGrid,
            v1ButtonsBox,
            v2ButtonsBox,
            Label("Operation Log:").apply { style = "-fx-font-weight: bold;" },
            miniLogArea,
        ).apply {
            VBox.setVgrow(miniLogArea, Priority.ALWAYS)
        }

        val scrollRight = ScrollPane(rightPane).apply {
            isFitToWidth = true
            isFitToHeight = true
        }

        val splitPane = SplitPane(leftContainer, scrollRight).apply {
            setDividerPositions(0.35)
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        root.children.addAll(modeBar, splitPane)
        return root
    }
}
