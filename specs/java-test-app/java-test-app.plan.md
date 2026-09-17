# Implementation Plan: Kuksa Java SDK Java Test App

## Executive Overview
- **Associated Specification:** `specs/drafts/java-test-app.spec.md`
- **Visual Wireframe Preview:** `specs/drafts/java-test-app.mockup.html`
- **Architectural Pattern:** Single-Stage JavaFX app; single `DataBrokerViewModel` (pure Kotlin + `StateFlow`); JavaFX controllers observe via `kotlinx-coroutines-javafx`; Clean separation between ViewModel (no JavaFX imports) and Controllers (JavaFX-only).
- **Target Frameworks & Technologies:**
  - JDK 21, Kotlin 1.9.22 (JVM 11 bytecode target — matches project)
  - JavaFX 21 LTS (`org.openjfx.javafxplugin:0.1.0`; modules: `javafx.controls`)
  - `grpc-netty-shaded:1.65.1` (matches project `libs.versions.toml`)
  - `kotlinx-coroutines-core:1.8.1` + `kotlinx-coroutines-javafx:1.8.1` (matches project)
  - `org.eclipse.velocitas.vss-processor-plugin:0.1.2` (KSP)
  - `com.charleskorn.kaml:kaml:0.61.0` (YAML for mock-provider)
- **Testing Frameworks:** JUnit 5 + Kotest 5.8.1 (already in project), MockK 1.13.10, TestFX 4.0.18 (UI), existing `InsecureDataBrokerDockerContainer` (integration).
- **Testing Strategy:** Strict TDD — write failing test → implement minimum code to pass → refactor.

---

## Execution Sequence
1. Phase A0: Host mock environment — Docker Compose + `mock-environment/behavior-rules.yaml`
2. Phase A1: `:mock-provider` Gradle module scaffold
3. Phase A2: MockProvider — `BehaviorRuleEngine` (TDD)
4. Phase A3: MockProvider — provider stream + main loop
5. Phase B: `:java-test-app` Gradle module scaffold + VSS plugin wiring
6. Phase C: Domain models — `ConnectionConfig`, `LogEntry`, `SignalItem`, `ConfigValidator`
7. Phase D: `DataBrokerViewModel` — connect / disconnect / state machine
8. Phase E: ViewModel v1 API actions — all 8 methods
9. Phase F: ViewModel v2 API actions — all 10 methods
10. Phase G: JavaFX UI — `MainApp` + tab scaffold + `ConnectionController`
11. Phase H: JavaFX UI — `SignalController` (browser + detail, v1 + v2 buttons)
12. Phase I: JavaFX UI — `ActuatorController` (single, batch, provider stream, streamed update)
13. Phase J: JavaFX UI — `SubscriptionController` + `LogController`
14. Phase K: Integration tests — Docker Databroker + in-process MockProvider
15. Phase L: TestFX UI tests

---

## Atomic Task Breakdown

### Task 0A: Host Mock Environment
- **Goal:** Create `mock-environment/` with Docker Compose for the databroker and initial behavior-rules YAML.
- **Target Files:**
  - `mock-environment/docker-compose.yml`
  - `mock-environment/behavior-rules.yaml`
  - `mock-environment/README.md`
- **Step-by-Step Execution:**
  1. Create `mock-environment/docker-compose.yml`:
     ```yaml
     services:
       databroker:
         image: ghcr.io/eclipse-kuksa/kuksa-databroker:main
         ports:
           - "55556:55556"
         command: ["--port", "55556", "--insecure"]
         healthcheck:
           test: ["CMD-SHELL", "grpc_health_probe -addr=:55556 || exit 1"]
           interval: 5s
           timeout: 3s
           retries: 5
     ```
  2. Create `mock-environment/behavior-rules.yaml` with the 5 rules from spec §3.0.
  3. Write `mock-environment/README.md` with the 3-command startup procedure from spec §3.0.
- **Validation Gate:**
  - `docker compose -f mock-environment/docker-compose.yml up -d && docker compose -f mock-environment/docker-compose.yml ps` shows databroker healthy.

---

### Task 0B: `:mock-provider` Gradle Module Scaffold
- **Goal:** Add `:mock-provider` JVM module with `application` plugin; compiles clean with an empty `main()`.
- **Target Files:**
  - `settings.gradle.kts` — add `include(":mock-provider")`
  - `mock-provider/build.gradle.kts`
  - `mock-provider/src/main/kotlin/org/eclipse/kuksa/mockprovider/Main.kt`
- **Step-by-Step Execution:**
  1. Add `include(":mock-provider")` to `settings.gradle.kts` (after existing includes).
  2. Create `mock-provider/build.gradle.kts`:
     ```kotlin
     import org.jetbrains.kotlin.gradle.dsl.JvmTarget

     plugins {
         id("application")
         kotlin("jvm")
     }

     java {
         sourceCompatibility = JavaVersion.VERSION_11
         targetCompatibility = JavaVersion.VERSION_11
     }

     kotlin {
         compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
     }

     application {
         mainClass.set("org.eclipse.kuksa.mockprovider.MainKt")
     }

     dependencies {
         implementation(project(":kuksa-java-sdk"))
         implementation("io.grpc:grpc-netty-shaded:1.65.1")
         implementation(libs.kotlinx.coroutines.core)
         implementation("com.charleskorn.kaml:kaml:0.61.0")
         testImplementation(libs.kotest)
         testImplementation(libs.mockk)
         testImplementation(libs.kotlinx.coroutines.test)
     }
     ```
  3. Create `Main.kt`:
     ```kotlin
     package org.eclipse.kuksa.mockprovider
     fun main(args: Array<String>) {
         println("MockProvider starting…")
     }
     ```
- **Validation Gate:**
  - `./gradlew :mock-provider:compileKotlin` exits 0.
  - `./gradlew :mock-provider:run` prints "MockProvider starting…" and exits cleanly.

---

### Task 0C: MockProvider — BehaviorRuleEngine (TDD)
- **Goal:** Implement pure domain logic: YAML loading, rule matching, direct/ramp/toggle transforms. Zero JavaFX / gRPC coupling.
- **Target Files:**
  - `mock-provider/src/main/kotlin/.../BehaviorRule.kt`
  - `mock-provider/src/main/kotlin/.../BehaviorRuleEngine.kt`
  - `mock-provider/src/main/kotlin/.../RuleLoader.kt`
  - `mock-provider/src/test/kotlin/.../BehaviorRuleEngineTest.kt`
- **Step-by-Step Execution:**
  1. Write `BehaviorRuleEngineTest` with UT-5 (direct), UT-6 (ramp 10 steps), UT-7 (toggle). Tests fail red.
  2. Define data classes:
     ```kotlin
     @Serializable
     data class BehaviorRule(
         val actuator: String,
         val sensor: String,
         @SerialName("delay_ms") val delayMs: Long,
         val transform: Transform,
     )

     @Serializable
     enum class Transform { DIRECT, RAMP, TOGGLE }

     @Serializable
     data class RuleConfig(val rules: List<BehaviorRule>)
     ```
  3. Implement `BehaviorRuleEngine`:
     ```kotlin
     class BehaviorRuleEngine(val rules: List<BehaviorRule>) {
         val actuatorPaths: List<String> get() = rules.map { it.actuator }
         fun findRule(actuatorPath: String): BehaviorRule?
         fun computeDirectValue(rule: BehaviorRule, target: Types.Value): Types.Value
         fun computeRampSteps(rule: BehaviorRule, current: Float, target: Float, steps: Int = 10): List<Float>
         fun computeToggle(rule: BehaviorRule, current: Types.Value): Types.Value
     }
     ```
  4. Implement `RuleLoader.load(path: String): List<BehaviorRule>` using kaml.
- **Validation Gate (TDD):**
  - `./gradlew :mock-provider:test --tests "*.BehaviorRuleEngineTest"` — UT-5, UT-6, UT-7 pass.

---

### Task 0D: MockProvider — Provider Stream & Main Loop
- **Goal:** Connect to databroker, register actuator ownership, handle `BatchActuateStreamRequest`, apply rules, publish sensor updates. Add CLI args for host/port/rules.
- **Target Files:**
  - `mock-provider/src/main/kotlin/.../MockProvider.kt`
  - `mock-provider/src/main/kotlin/.../Main.kt` (updated)
- **Step-by-Step Execution:**
  1. Implement `MockProvider(host, port, rulesPath)`:
     - `start()`: build Netty `ManagedChannel`; `DataBrokerConnector.connect()`; call `openProviderStream`; send `ProvideActuationRequest` for all `engine.actuatorPaths`; block collecting stream.
     - `responseObserver.onNext()`: detect `BatchActuateStreamRequest`; launch `handleActuate()` coroutine; send `BatchActuateStreamResponse` with empty error map.
     - `handleActuate()`: for each `ActuateRequest`, call `engine.findRule()`; `applyRule()`:
       - `DIRECT`: `delay(rule.delayMs)`; `publishSensor(sensor, computeDirectValue(...))`.
       - `RAMP`: `fetchCurrentValue(sensor)` via `fetchValue`; iterate `computeRampSteps`; publish each step; `delay(rule.delayMs / 10)` between steps.
       - `TOGGLE`: `fetchCurrentValue`; `publishSensor(sensor, computeToggle(...))`.
     - `onError()`: log; exponential backoff (1s, 2s, 4s, 8s, 16s); reconnect up to 5 times; then exit 1.
  2. Update `Main.kt` to parse `--host`, `--port`, `--rules` args; instantiate `MockProvider`; `runBlocking { start() }`.
- **Validation Gate:**
  - Manual: `docker compose up -d` + `./gradlew :mock-provider:run`; from a separate JVM invoke actuate on `CruiseControl.SpeedSet`; log shows ramp steps.
  - IT-7M, IT-8M, IT-9M, IT-10M pass (Task K).

---

### Task 1: `:java-test-app` Gradle Module Scaffold + VSS Plugin
- **Goal:** Add `:java-test-app` module with `application` + `kotlin("jvm")` + JavaFX plugin + VSS processor plugin. Verify generated VSS classes exist after build.
- **Target Files:**
  - `settings.gradle.kts` — add `include(":java-test-app")`
  - `java-test-app/build.gradle.kts`
  - `java-test-app/src/main/kotlin/org/eclipse/kuksa/testapp/Main.kt`
- **Step-by-Step Execution:**
  1. Add `include(":java-test-app")` to `settings.gradle.kts`.
  2. Create `java-test-app/build.gradle.kts`:
     ```kotlin
     import org.jetbrains.kotlin.gradle.dsl.JvmTarget

     plugins {
         id("application")
         kotlin("jvm")
         id("com.google.devtools.ksp")
         id("org.openjfx.javafxplugin") version "0.1.0"
         id("org.eclipse.velocitas.vss-processor-plugin") version "0.1.2"
     }

     java {
         sourceCompatibility = JavaVersion.VERSION_11
         targetCompatibility = JavaVersion.VERSION_11
     }

     kotlin {
         compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
     }

     application {
         mainClass.set("org.eclipse.kuksa.testapp.MainKt")
     }

     javafx {
         version = "21"
         modules = listOf("javafx.controls")
     }

     vssProcessor {
         vssDefinitionPath = "$rootDir/vss/vss_rel_4.1.yaml"
         generatedSourceOutputDir = "${layout.buildDirectory.get()}/generated/ksp/main/kotlin"
     }

     dependencies {
         implementation(project(":kuksa-java-sdk"))
         implementation("io.grpc:grpc-netty-shaded:1.65.1")
         implementation(libs.kotlinx.coroutines.core)
         implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.8.1")
         testImplementation(libs.kotest)
         testImplementation(libs.mockk)
         testImplementation(libs.kotlinx.coroutines.test)
         testImplementation("org.testfx:testfx-junit5:4.0.18")
     }
     ```
  3. Create minimal `Main.kt`:
     ```kotlin
     package org.eclipse.kuksa.testapp
     import javafx.application.Application
     fun main(args: Array<String>) = Application.launch(TestApp::class.java, *args)
     class TestApp : Application() {
         override fun start(stage: javafx.stage.Stage) {
             stage.title = "Kuksa Java SDK Test App"
             stage.show()
         }
     }
     ```
- **Validation Gate:**
  - `./gradlew :java-test-app:compileKotlin` exits 0.
  - `find java-test-app/build/generated -name "Vss*.kt" | wc -l` returns ≥ 1.
  - `./gradlew :java-test-app:run` opens a blank JavaFX window without crashing.

---

### Task 2: Domain Models — `ConnectionConfig`, `LogEntry`, `SignalItem`, `ConfigValidator`
- **Goal:** Pure Kotlin domain types with zero JavaFX or gRPC coupling; cover spec §4.5 exactly.
- **Target Files:**
  - `java-test-app/src/main/kotlin/.../model/ConnectionConfig.kt`
  - `java-test-app/src/main/kotlin/.../model/LogEntry.kt`
  - `java-test-app/src/main/kotlin/.../model/SignalItem.kt`
  - `java-test-app/src/main/kotlin/.../domain/ConfigValidator.kt`
  - `java-test-app/src/test/kotlin/.../domain/ConfigValidatorTest.kt`
- **Step-by-Step Execution:**
  1. Write `ConfigValidatorTest` with UT-1 (empty host) and UT-2 (port=0). Tests fail red.
  2. Implement all model classes exactly as described in spec §4.5.
  3. Implement `ConfigValidator.validate(config: ConnectionConfig): ValidationResult` (sealed class: `Ok` | `Error(message)`).
  4. Write `LogEntryTest` for UT-3 and `SignalItemTest` for UT-4.
- **Validation Gate (TDD):**
  - `./gradlew :java-test-app:test --tests "*.ConfigValidatorTest"` — UT-1 and UT-2 pass.
  - `./gradlew :java-test-app:test --tests "*.LogEntryTest"` — UT-3 passes.
  - `./gradlew :java-test-app:test --tests "*.SignalItemTest"` — UT-4 passes.

---

### Task 3: `DataBrokerViewModel` — Connect / Disconnect / State Machine
- **Goal:** Pure Kotlin ViewModel (no JavaFX imports) using `StateFlow`. Covers spec Workflow 1 and Workflow 6.
- **Target Files:**
  - `java-test-app/src/main/kotlin/.../viewmodel/DataBrokerViewModel.kt`
  - `java-test-app/src/test/kotlin/.../viewmodel/DataBrokerViewModelTest.kt`
- **Step-by-Step Execution:**
  1. Write `DataBrokerViewModelTest`:
     - Mocked `DataBrokerConnector.connect()` throws `DataBrokerException` → `connectionState.value == ERROR`.
     - `disconnect()` on connected ViewModel → `connectionState.value == DISCONNECTED`.
  2. Implement `DataBrokerViewModel`:
     ```kotlin
     class DataBrokerViewModel(
         private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
     ) {
         private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
         val connectionState: StateFlow<ConnectionState> = _connectionState
         private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
         val logEntries: StateFlow<List<LogEntry>> = _logEntries
         private val _activeSubscriptions = MutableStateFlow<Map<String, Job>>(emptyMap())
         val activeSubscriptions: StateFlow<Map<String, Job>> = _activeSubscriptions

         internal var connection: DataBrokerConnection? = null
         private var subscriptionJobs = mutableMapOf<String, Job>()

         fun connect(config: ConnectionConfig) { /* validate → build channel → connect → update state */ }
         fun disconnect() { /* cancel jobs → connection.disconnect() → DISCONNECTED */ }
         fun clearLog() { _logEntries.value = emptyList() }
         internal fun appendLog(entry: LogEntry) {
             _logEntries.value = (_logEntries.value + entry).takeLast(1000)
         }
     }
     ```
  3. Wire `DisconnectListener` to emit `DISCONNECTED` on channel shutdown.
- **Validation Gate (TDD):**
  - `./gradlew :java-test-app:test --tests "*.DataBrokerViewModelTest"` — all scenarios pass.

---

### Task 4: ViewModel v1 API Actions (all 8)
- **Goal:** Implement all 8 `KuksaValV1Protocol` ViewModel methods; each appends `REQUEST` + `RESPONSE` log entries.
- **Target Files:**
  - `java-test-app/src/main/kotlin/.../viewmodel/DataBrokerViewModel.kt` (extended)
  - `java-test-app/src/test/kotlin/.../viewmodel/V1ApiViewModelTest.kt`
- **Step-by-Step Execution:**
  1. Write `V1ApiViewModelTest` with mocked `KuksaValV1Protocol` for each of the 8 methods. Tests fail red.
  2. Implement:
     ```kotlin
     fun fetchV1(vssPath: String, field: Types.Field) { scope.launch { ... } }
     fun fetchNodeV1(vssNode: VssNode) { scope.launch { ... } }
     fun updateV1(vssPath: String, dataPoint: Datapoint, field: Types.Field) { scope.launch { ... } }
     fun updateNodeV1(vssNode: VssNode) { scope.launch { ... } }
     fun subscribeV1Listener(vssPath: String, field: Types.Field) { /* VssPathListener callback → appendLog */ }
     fun subscribeV1Flow(vssPath: String, field: Types.Field) { /* collect Flow → appendLog; store Job */ }
     fun subscribeNodeV1(vssNode: VssNode) { /* VssNodeListener → appendLog; store Job */ }
     fun openStreamedUpdateV1(): StreamObserver<KuksaValV1.StreamedUpdateRequest> { ... }
     fun unsubscribe(key: String) { subscriptionJobs[key]?.cancel() }
     ```
- **Validation Gate (TDD):**
  - `./gradlew :java-test-app:test --tests "*.V1ApiViewModelTest"` — all 8 method scenarios pass.

---

### Task 5: ViewModel v2 API Actions (all 10)
- **Goal:** Implement all 10 `KuksaValV2Protocol` ViewModel methods; same log-entry pattern.
- **Target Files:**
  - `java-test-app/src/main/kotlin/.../viewmodel/DataBrokerViewModel.kt` (extended)
  - `java-test-app/src/test/kotlin/.../viewmodel/V2ApiViewModelTest.kt`
- **Step-by-Step Execution:**
  1. Write `V2ApiViewModelTest` with mocked `KuksaValV2Protocol`. Tests fail red.
  2. Implement:
     ```kotlin
     fun fetchValueV2(signalId: SignalID) { scope.launch { ... } }
     fun fetchValuesV2(signalIds: List<SignalID>) { scope.launch { ... } }
     fun publishValueV2(signalId: SignalID, datapoint: Types.Datapoint) { scope.launch { ... } }
     fun actuateV2(signalId: SignalID, value: Types.Value) { scope.launch { ... } }
     fun batchActuateV2(signalIds: List<SignalID>, value: Types.Value) { scope.launch { ... } }
     fun subscribeByPathV2(paths: List<String>, bufferSize: Int = 10) { /* collect Flow; store Job */ }
     fun subscribeByIdV2(signalIds: List<Int32Value>, bufferSize: Int = 10) { /* collect Flow; store Job */ }
     fun openProviderStreamV2(): StreamObserver<KuksaValV2.OpenProviderStreamRequest> { ... }
     fun listMetadataV2(root: String, filter: String) { scope.launch { ... } }
     fun fetchServerInfoV2() { scope.launch { ... } }
     ```
  3. `openProviderStreamV2()` stores returned sender `StreamObserver` in a ViewModel field; exposes `sendProviderMessage(request)`.
- **Validation Gate (TDD):**
  - `./gradlew :java-test-app:test --tests "*.V2ApiViewModelTest"` — all 10 scenarios pass.

---

### Task 6: `VssSignalRepository` — Generated VSS Signal List
- **Goal:** Walk the generated `VssVehicle` class hierarchy reflectively to produce a flat `List<SignalItem>` at runtime.
- **Target Files:**
  - `java-test-app/src/main/kotlin/.../repository/VssSignalRepository.kt`
  - `java-test-app/src/test/kotlin/.../repository/VssSignalRepositoryTest.kt`
- **Step-by-Step Execution:**
  1. Write `VssSignalRepositoryTest`: `allSignals()` returns ≥ 50 items; each has non-empty `vssPath`.
  2. Implement `VssSignalRepository`:
     ```kotlin
     object VssSignalRepository {
         fun allSignals(): List<SignalItem> =
             flattenNode(VssVehicle()).map { SignalItem.fromVssNode(it) }

         private fun flattenNode(node: VssNode): List<VssNode> =
             listOf(node) + node.children.flatMap { flattenNode(it) }
     }
     ```
- **Validation Gate (TDD):**
  - `./gradlew :java-test-app:test --tests "*.VssSignalRepositoryTest"` — ≥ 50 signals, no empty paths.

---

### Task 7: JavaFX Main Window + Connection Tab
- **Goal:** Build the `TestApp` + `MainController` (TabPane + 5 tabs) + `ConnectionController` matching `ZONE-CONNECTION` wireframe. All non-connection tabs disabled until connected.
- **Target Files:**
  - `java-test-app/src/main/kotlin/.../TestApp.kt`
  - `java-test-app/src/main/kotlin/.../ui/MainController.kt`
  - `java-test-app/src/main/kotlin/.../ui/ConnectionController.kt`
  - `java-test-app/src/androidTest/...` → `java-test-app/src/test/kotlin/.../ui/ConnectionControllerTest.kt`
- **Step-by-Step Execution:**
  1. Write `ConnectionControllerTest` (TestFX) for UI-1, UI-2, UI-3. Tests fail red.
  2. Build `TestApp.start()`:
     - Create `DataBrokerViewModel` singleton.
     - Build `TabPane` with 5 tabs; add `MainController`.
     - Collect `connectionState` on `Dispatchers.Main` (JavaFX thread) via `kotlinx-coroutines-javafx`; enable/disable tabs accordingly.
  3. Build `ConnectionController`:
     - `TextField` host, port, JWT; `CheckBox` TLS.
     - `ToggleButton` pair for v1/v2 API version.
     - Connect/Disconnect buttons wired to `viewModel.connect(config)` / `viewModel.disconnect()`.
     - Status `Label` bound to `connectionState` flow.
     - Error banner (`VBox`, red background) shown when `state == ERROR`.
  4. Coroutine collection pattern (used in all controllers):
     ```kotlin
     private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
     // in initialize():
     scope.launch {
         viewModel.connectionState.collect { state -> updateUI(state) }
     }
     ```
- **Validation Gate (TDD):**
  - TestFX: UI-1, UI-2, UI-3 pass.

---

### Task 7B: `DataTypeSerializer` + `PathHistory` (Free-form Path Support)
- **Goal:** Pure Kotlin domain utilities for the Raw Path input mode: value-string → proto `Datapoint`/`Types.Value` serialization, and a bounded path history ring buffer. No JavaFX coupling.
- **Target Files:**
  - `java-test-app/src/main/kotlin/.../domain/DataTypeSerializer.kt`
  - `java-test-app/src/main/kotlin/.../domain/PathHistory.kt`
  - `java-test-app/src/test/kotlin/.../domain/DataTypeSerializerTest.kt`
  - `java-test-app/src/test/kotlin/.../domain/PathHistoryTest.kt`
- **Step-by-Step Execution:**
  1. Write `DataTypeSerializerTest` with UT-8 (float), UT-9 (boolean), UT-10 (invalid). Tests fail red.
  2. Write `PathHistoryTest` with UT-11 (eviction at 20). Tests fail red.
  3. Implement `DataTypeSerializer`:
     ```kotlin
     object DataTypeSerializer {
         fun toDatapoint(dataType: String, value: String): Datapoint  // v1 Datapoint
         fun toValue(dataType: String, value: String): Types.Value     // v2 Types.Value
     }
     ```
     Supported `dataType` strings: `"boolean"`, `"float"`, `"double"`, `"int32"`, `"uint32"`, `"int64"`, `"uint64"`, `"string"`. Throws `DataTypeSerializationException` on parse failure.
  4. Implement `PathHistory`:
     ```kotlin
     class PathHistory(private val maxSize: Int = 20) {
         fun record(path: String)            // adds to front, evicts tail if > maxSize
         fun toList(): List<String>          // most-recent first
     }
     ```
- **Validation Gate (TDD):**
  - `./gradlew :java-test-app:test --tests "*.DataTypeSerializerTest" --tests "*.PathHistoryTest"` — UT-8, UT-9, UT-10, UT-11 all pass.

---

### Task 8: SignalController — Browser + Detail Panel
- **Goal:** Implement split-pane signal browser + detail panel; both VSS Model mode and Raw Path (free-form) mode; all v1 and v2 per-signal buttons wired to ViewModel.
- **Target Files:**
  - `java-test-app/src/main/kotlin/.../ui/SignalController.kt`
  - `java-test-app/src/test/kotlin/.../ui/SignalControllerTest.kt`
- **Step-by-Step Execution:**
  1. Add mode toolbar at top of Signals tab: `ToggleButton` pair **VSS Model** / **Raw Path**; DataType `ComboBox` (hidden in VSS Model mode).
  2. **VSS Model mode** — left pane:
     - `ListView<SignalItem>` from `VssSignalRepository.allSignals()`; `TextField` filter; type-filter buttons (All / Sensor / Actuator / Attr).
  3. **Raw Path mode** — left pane (swaps in when mode toggles):
     - `TextField` labelled "VSS Path"; History `ComboBox` (last 20 paths, in-memory via `PathHistory`); DataType `ComboBox` (boolean/float/double/int32/uint32/int64/uint64/string, default float).
     - Path `TextField` gets red border + error label if empty when an action is triggered.
  4. Right pane (shared between modes):
     - Metadata grid `GridPane` — hidden in Raw Path mode (no compile-time metadata available).
     - Value `TextField` + field `ComboBox` (v1 only).
     - **v1 button group** (when `activeApi == V1`):
       - Fetch (`FetchRequest(vssPath)`), Update (`UpdateRequest(vssPath, DataTypeSerializer.toDatapoint(...))`), Subscribe (path), Subscribe (flow).
       - *VSS Model only* (hidden in Raw Path): Fetch (Node), Update (Node), Subscribe (node), Streamed Update.
     - **v2 button group** (when `activeApi == V2`):
       - Fetch Value, Fetch Values (bulk), Publish Value, Subscribe (path), Subscribe by ID, List Metadata.
       - *VSS Model only* (hidden in Raw Path): — (all v2 buttons are path-based by design; no node-only v2 operations exist).
     - In Raw Path mode: on action, `PathHistory.record(path)` before dispatching to ViewModel.
  5. Mini-log `TextArea` at pane bottom; auto-scroll to last 50 entries for active signal/path.
  6. Bind API-version toggle to show/hide button groups via `isVisible` + `isManaged`.
- **Validation Gate (TDD):**
  - UI-4 (v2 buttons visible on toggle), UI-5 (node buttons hidden in Raw Path mode), UI-6 (empty path validation), UI-7 (fetch dispatched), UI-8 (history populated) — all pass via TestFX.
  - Unit (no JavaFX): `DataTypeSerializer.toDatapoint("float", "95.5")` returns correct `Datapoint`.

---

### Task 9: ActuatorController — Single, Batch, Provider Stream, Streamed Update
- **Goal:** Implement actuator tab covering all actuator-specific operations from spec §4.4.
- **Target Files:**
  - `java-test-app/src/main/kotlin/.../ui/ActuatorController.kt`
- **Step-by-Step Execution:**
  1. Mode toolbar: **VSS Model** / **Raw Path** `ToggleButton` pair (same pattern as SignalController).
  2. **VSS Model mode** left pane: `ListView<SignalItem>` filtered to `signalType == "actuator"`; `CheckBox` per row for batch selection.
     **Raw Path mode** left pane: Path `TextField` + DataType `ComboBox`; a **+ Add to Batch** button appends the entered path to an editable batch `ListView` for multi-path `batchActuate`.
  3. Right pane — **v2 section**:
     - Value `TextField` + **Actuate** button → `viewModel.actuateV2(signalId, value)`.
     - Batch value `TextField` + **Batch Actuate (N selected)** button → `viewModel.batchActuateV2(selectedIds, value)`; validates ≥1 selected.
     - **Provider Stream** subsection: Open Stream / Send Publish Values / Send Provide Actuation / Close buttons; `TextArea` for incoming `BatchActuateStreamRequest` messages.
  3. Right pane — **v1 section** (when v1 active):
     - Value `TextField` + field `ComboBox` (VALUE | ACTUATOR_TARGET) + **Update** button → `viewModel.updateV1(vssPath, dataPoint, field)`.
     - **Streamed Update ▶** button → `viewModel.openStreamedUpdateV1()`; exposes value input + Send/Stop buttons.
  4. Mini-log at bottom of right pane.
- **Validation Gate (TDD):**
  - Unit: Batch Actuate with 0 checkboxes selected → validation error label shown; ViewModel not called.

---

### Task 10: SubscriptionController + LogController
- **Goal:** Implement subscriptions live-log pane and full audit log tab.
- **Target Files:**
  - `java-test-app/src/main/kotlin/.../ui/SubscriptionController.kt`
  - `java-test-app/src/main/kotlin/.../ui/LogController.kt`
- **Step-by-Step Execution:**
  1. `SubscriptionController`:
     - Left: `ListView<SubscriptionEntry>` bound to `viewModel.activeSubscriptions`; each row shows path + latest value + Unsubscribe button.
     - Subscribe-by-path input + button → `viewModel.subscribeByPathV2(listOf(path))`.
     - Subscribe-by-ID input + button → `viewModel.subscribeByIdV2(signalIds)` (v2 only; hidden for v1).
     - Right: live `TextArea` log collecting all `Direction.EVENT` log entries; auto-scroll to bottom; Clear + Export buttons.
  2. `LogController`:
     - Full `TextArea` collecting all `logEntries` from ViewModel; colour-coded per `Direction` via CSS (blue=REQUEST, green=RESPONSE, red=ERROR, yellow=EVENT).
     - Clear button → `viewModel.clearLog()`.
     - Export button → writes log to `Files.createTempFile()`; opens system file chooser.
     - **Server Info (v2)** button → `viewModel.fetchServerInfoV2()`; result shown in `Alert(INFORMATION)`.
- **Validation Gate (TDD):**
  - Unit: Unsubscribe cancels job; signal removed from `activeSubscriptions` map within 100 ms.
  - Unit: `clearLog()` resets `logEntries` to empty list.

---

### Task 11: Integration Tests — Docker Databroker + In-Process MockProvider
- **Goal:** Run all API round-trips (IT-1 through IT-10M) against real Docker Databroker; actuate feedback tests with in-process MockProvider.
- **Target Files:**
  - `java-test-app/src/test/kotlin/.../integration/V1IntegrationTest.kt`
  - `java-test-app/src/test/kotlin/.../integration/V2IntegrationTest.kt`
  - `java-test-app/src/test/kotlin/.../integration/MockProviderIntegrationTest.kt`
- **Step-by-Step Execution:**
  1. Reuse `InsecureDataBrokerDockerContainer` from `:test-core`; add `:test-core` to `testImplementation` deps.
  2. `V1IntegrationTest`: scenarios IT-1 and IT-6 — use `DataBrokerConnector` directly (not ViewModel).
  3. `V2IntegrationTest`: scenarios IT-2, IT-3, IT-4, IT-5 — direct SDK calls; assert on proto fields.
  4. `MockProviderIntegrationTest`:
     - `@BeforeEach`: start `InsecureDataBrokerDockerContainer`; instantiate `MockProvider`; start in background coroutine; wait for `ProvideActuationRequest` ACK (5 s timeout).
     - IT-7M (ramp), IT-8M (toggle), IT-9M (batch), IT-10M (no-provider UNAVAILABLE).
     - `@AfterEach`: cancel MockProvider coroutine; stop container.
  5. All assertions on proto field values, not just "no exception".
- **Validation Gate (TDD):**
  - `./gradlew :java-test-app:test :mock-provider:test -Pdocker=true` — 10 integration scenarios pass.

---

### Task 12: TestFX UI Tests
- **Goal:** Automate UI scenarios UI-1 through UI-4 on headless / headed JVM without device dependency.
- **Target Files:**
  - `java-test-app/src/test/kotlin/.../ui/ConnectionControllerTest.kt`
  - `java-test-app/src/test/kotlin/.../ui/ApiVersionSwitchTest.kt`
- **Step-by-Step Execution:**
  1. Use `ApplicationTest` (TestFX) base class; inject a `DataBrokerViewModel` with a `TestScope`.
  2. UI-1: assert Connect button enabled; non-connection tabs disabled.
  3. UI-2: emit `CONNECTING` via `TestScope`; assert Connect button disabled; status label contains "Connecting".
  4. UI-3: emit `ERROR("timeout")`; assert status label contains "Error"; Connect button re-enabled.
  5. UI-4: emit `CONNECTED`; click v2 toggle; assert v2 buttons visible in Signals tab.
  6. UI-5: click "Raw Path" toggle; assert `ListView` hidden; Path `TextField` visible; "Fetch (Node)" button hidden.
  7. UI-6: Raw Path mode; leave path empty; click Fetch; assert `TextField` has CSS class `error`; ViewModel mock not called.
  8. UI-7: Raw Path mode; type "Vehicle.Speed"; select float; click Fetch (v1); assert mock `viewModel.fetchV1("Vehicle.Speed", ...)` called.
  9. UI-8: after UI-7, open History `ComboBox`; assert "Vehicle.Speed" is first item.
- **Validation Gate (TDD):**
  - `./gradlew :java-test-app:test --tests "*.ConnectionControllerTest" --tests "*.ApiVersionSwitchTest" --tests "*.SignalControllerTest"` — all 9 UI scenarios pass.

---

## Verification and Rollback Strategy

### Full test suite
```bash
# Unit tests (no Docker needed)
./gradlew :mock-provider:test :java-test-app:testDebugUnitTest

# Integration tests (requires Docker daemon)
docker compose -f mock-environment/docker-compose.yml up -d
./gradlew :mock-provider:test :java-test-app:test -Pdocker=true
docker compose -f mock-environment/docker-compose.yml down

# UI tests (requires display / headless JVM with JavaFX)
./gradlew :java-test-app:test --tests "*.ConnectionControllerTest"
```

### Manual E2E smoke test
```bash
# 1. Start host environment
docker compose -f mock-environment/docker-compose.yml up -d
./gradlew :mock-provider:run &

# 2. Launch test app
./gradlew :java-test-app:run
# In app: Connection tab → host=localhost, port=55556 → Connect
# Select kuksa.val.v2 → Signals tab → Vehicle.ADAS.CruiseControl.SpeedSet
# Enter 120.0 → Actuate
# Subscriptions tab: Vehicle.Speed should ramp to 120.0 in ~10 steps
```

### Build Verification
- `./gradlew :java-test-app:installDist :mock-provider:installDist` — both must succeed.
- Distributions at `java-test-app/build/install/java-test-app/` and `mock-provider/build/install/mock-provider/`.

### Rollback
```bash
git revert HEAD
./gradlew :java-test-app:clean :mock-provider:clean
```

### VSS Plugin Regeneration
```bash
rm -rf java-test-app/build/generated
./gradlew :java-test-app:kspKotlin
```
