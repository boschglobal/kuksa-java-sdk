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
import javafx.scene.control.CheckBox
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
import org.eclipse.kuksa.proto.v1.KuksaValV1
import org.eclipse.kuksa.testapp.domain.DataTypeSerializer
import org.eclipse.kuksa.testapp.domain.PathHistory
import org.eclipse.kuksa.testapp.model.SignalItem
import org.eclipse.kuksa.testapp.repository.VssSignalRepository
import org.eclipse.kuksa.testapp.viewmodel.DataBrokerViewModel
import org.eclipse.kuksa.proto.v2.Types as V2Types

// UI layout spacing and proportions are self-documenting in context; named constants would add noise.
@Suppress("MagicNumber")
class ActuatorController(
    private val viewModel: DataBrokerViewModel,
    private val uiScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
) {
    val isVssModelMode = ToggleButton("VSS Model").apply { isSelected = true }
    val isRawPathMode = ToggleButton("Raw Path")

    val searchFilterField = TextField().apply { promptText = "Filter actuators..." }
    val pathHistory = PathHistory(20)

    val rawPathField = TextField("Vehicle.ADAS.CruiseControl.SpeedSet")
    val rawHistoryCombo = ComboBox<String>()
    val rawDataTypeCombo = ComboBox(
        FXCollections.observableArrayList("float", "boolean", "int32", "uint32", "int64", "uint64", "double", "string"),
    ).apply { selectionModel.select("float") }

    val checkedActuators = mutableSetOf<String>()
    private val allActuatorsList = FXCollections.observableArrayList<SignalItem>()
    private val filteredActuators = FilteredList(allActuatorsList) { true }
    val actuatorListView = ListView(filteredActuators)

    val currentPathLabel = Label("-").apply { style = "-fx-font-weight: bold;" }
    val currentTypeLabel = Label("-")
    val currentDataTypeLabel = Label("-")

    val targetValueField = TextField("120.0").apply { promptText = "Target actuation value" }
    val actuateBtn = Button("Actuate (Single)")
    val batchActuateBtn = Button("Batch Actuate (Checked)")

    val streamedUpdateV1Btn = Button("Open StreamedUpdate (v1)")
    val sendStreamedUpdateV1Btn = Button("Send v1 Stream Update")

    val providerStreamV2Btn = Button("Open ProviderStream (v2)")
    val sendProvideActuationV2Btn = Button("Register Actuators on Stream")

    val miniLogArea = TextArea().apply {
        isEditable = false
        maxHeight = Double.MAX_VALUE
        style = "-fx-font-family: monospace; -fx-font-size: 11px;"
    }

    private var selectedActuator: SignalItem? = null
    val view: Node by lazy { buildView() }

    init {
        val actuators = VssSignalRepository.actuators().ifEmpty {
            VssSignalRepository.allSignals().filter {
                it.signalType == "actuator" ||
                    it.vssPath.contains("Target") ||
                    it.vssPath.contains("Set") ||
                    it.vssPath.contains("IsOn") ||
                    it.vssPath.contains("Selected")
            }
        }
        allActuatorsList.setAll(actuators)

        actuatorListView.setCellFactory {
            object : ListCell<SignalItem>() {
                private val checkBox = CheckBox()
                override fun updateItem(item: SignalItem?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        graphic = null
                        text = null
                    } else {
                        checkBox.isSelected = checkedActuators.contains(item.vssPath)
                        checkBox.setOnAction {
                            if (checkBox.isSelected) {
                                checkedActuators.add(item.vssPath)
                            } else {
                                checkedActuators.remove(item.vssPath)
                            }
                        }
                        graphic = checkBox
                        text = "[${item.signalType.uppercase()}] ${item.vssPath}"
                    }
                }
            }
        }

        actuatorListView.selectionModel.selectedItemProperty().addListener { _, _, newSelection ->
            selectedActuator = newSelection
            newSelection?.let {
                currentPathLabel.text = it.vssPath
                currentTypeLabel.text = it.signalType
                currentDataTypeLabel.text = it.dataType
            }
        }

        searchFilterField.textProperty().addListener { _, _, text ->
            filteredActuators.setPredicate { item ->
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

    private fun getSelectedOrRawPaths(): List<String> {
        return if (isVssModelMode.isSelected) {
            if (checkedActuators.isNotEmpty()) {
                checkedActuators.toList()
            } else {
                val selected = selectedActuator?.vssPath ?: actuatorListView.selectionModel.selectedItem?.vssPath
                if (selected != null) {
                    listOf(selected)
                } else {
                    listOf("Vehicle.ADAS.CruiseControl.SpeedSet")
                }
            }
        } else {
            val path = rawPathField.text.trim()
            pathHistory.record(path)
            rawHistoryCombo.items = FXCollections.observableArrayList(pathHistory.toList())
            listOf(path)
        }
    }

    private fun getActiveDataType(): String {
        return if (isVssModelMode.isSelected) {
            selectedActuator?.dataType ?: "float"
        } else {
            rawDataTypeCombo.value ?: "float"
        }
    }

    private fun wireActions() {
        actuateBtn.setOnAction {
            val path = getSelectedOrRawPaths().firstOrNull() ?: return@setOnAction
            val signalId = V2Types.SignalID.newBuilder().setPath(path).build()
            val dataType = getActiveDataType()
            val valObj = DataTypeSerializer.toValue(dataType, targetValueField.text.trim())
            viewModel.actuateV2(signalId, valObj)
        }

        batchActuateBtn.setOnAction {
            val paths = getSelectedOrRawPaths()
            val signalIds = paths.map { V2Types.SignalID.newBuilder().setPath(it).build() }
            val dataType = getActiveDataType()
            val valObj = DataTypeSerializer.toValue(dataType, targetValueField.text.trim())
            viewModel.batchActuateV2(signalIds, valObj)
        }

        streamedUpdateV1Btn.setOnAction {
            viewModel.openStreamedUpdateV1()
        }

        sendStreamedUpdateV1Btn.setOnAction {
            val path = getSelectedOrRawPaths().firstOrNull() ?: "Vehicle.Speed"
            val sender = viewModel.activeStreamedUpdateV1Sender
            if (sender != null) {
                val dp = DataTypeSerializer.toDatapoint(getActiveDataType(), targetValueField.text.trim())
                val updateEntry = KuksaValV1.EntryUpdate.newBuilder()
                    .setEntry(
                        org.eclipse.kuksa.proto.v1.Types.DataEntry.newBuilder().setPath(path).setValue(dp).build(),
                    )
                    .build()
                val req = KuksaValV1.StreamedUpdateRequest.newBuilder().addUpdates(updateEntry).build()
                sender.onNext(req)
            }
        }

        providerStreamV2Btn.setOnAction {
            viewModel.openProviderStreamV2()
        }

        sendProvideActuationV2Btn.setOnAction {
            val paths = getSelectedOrRawPaths()
            val sender = viewModel.activeProviderStreamV2Sender
            if (sender != null) {
                val signalIds = paths.map { V2Types.SignalID.newBuilder().setPath(it).build() }
                val provideReq = org.eclipse.kuksa.proto.v2.KuksaValV2.ProvideActuationRequest.newBuilder()
                    .addAllActuatorIdentifiers(signalIds)
                    .build()
                val openReq = org.eclipse.kuksa.proto.v2.KuksaValV2.OpenProviderStreamRequest.newBuilder()
                    .setProvideActuationRequest(provideReq)
                    .build()
                sender.onNext(openReq)
            }
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

        val vssModelLeftPane = VBox(8.0, searchFilterField, actuatorListView).apply {
            VBox.setVgrow(actuatorListView, Priority.ALWAYS)
        }

        val rawPathLeftPane = VBox(
            8.0,
            Label("Actuator Path:"),
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
        }
        isRawPathMode.setOnAction {
            vssModelLeftPane.isVisible = false
            rawPathLeftPane.isVisible = true
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
            add(Label("Target Value:"), 0, 3)
            add(targetValueField, 1, 3)
        }

        val actuateButtonsBox = VBox(
            8.0,
            Label("Actuation Operations:").apply { style = "-fx-font-weight: bold;" },
            HBox(10.0, actuateBtn, batchActuateBtn),
            Label("Streaming Interfaces:").apply { style = "-fx-font-weight: bold;" },
            HBox(10.0, streamedUpdateV1Btn, sendStreamedUpdateV1Btn),
            HBox(10.0, providerStreamV2Btn, sendProvideActuationV2Btn),
        )

        val rightPane = VBox(
            12.0,
            detailGrid,
            actuateButtonsBox,
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
