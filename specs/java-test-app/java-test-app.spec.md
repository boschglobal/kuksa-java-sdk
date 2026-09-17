# Feature Specification: Kuksa Java SDK Java Test App

## 1. Context and Architectural Intent
- **Feature Identifier:** SPEC-JAVA-TEST-APP-001
- **Supersedes:** SPEC-ANDROID-TEST-APP-001 (Android variant abandoned; JVM desktop app chosen)
- **Target Persona / Client:** SDK developer (Andre Weber) testing the kuksa-java-sdk against a live or local KUKSA Databroker from a desktop/laptop JVM environment.
- **Core Intent:** Provide a self-contained JavaFX desktop application (`:java-test-app` Gradle module) that exercises **every public API method** of both `KuksaValV1Protocol` and `KuksaValV2Protocol`, using VSS signal classes generated at build time from `vss/vss_rel_4.1.yaml` via the velocitas VSS processor Gradle plugin.
- **Problem Justification:** The existing `samples/` module is a non-interactive JVM CLI that runs a single script and exits. Developers need a persistent, interactive desktop UI to iteratively invoke individual SDK operations, observe responses, and verify subscription behaviour without recompiling.

---

## 2. System Domain and Workspace Alignment
- **Impacted Systems:** New Gradle sub-module `:java-test-app` inside the existing multi-module project. New sub-module `:mock-provider`. No changes to `:kuksa-java-sdk`, `:vss-core`, or `:samples`.
- **Domain Invariants:**
  - `Invariant-1`: A `DataBrokerConnection` must be established before any API call is issued; all UI controls except the Connection tab are disabled while disconnected.
  - `Invariant-2`: The active API version (v1 / v2) is a session-global setting; switching it disconnects, rebuilds the `ManagedChannel`, and re-connects without clearing host/port/JWT fields.
  - `Invariant-3`: VSS signal data classes are **generated at compile time** by the velocitas plugin from `vss/vss_rel_4.1.yaml`; no hand-written signal stubs are needed.
  - `Invariant-4`: The host-side MockProvider **must be running** before actuator operations are exercised; without a registered provider the databroker returns `UNAVAILABLE` for `actuate()` calls.
  - `Invariant-5`: MockProvider behavior rules execute within 500 ms of receiving an actuate request; sensor values are updated via `publishValue` on the same databroker so in-flight subscriptions fire.

- **Domain Lexicon:**
  - `DataBrokerConnector`: SDK class that owns the gRPC `ManagedChannel` and produces a `DataBrokerConnection`.
  - `KuksaValV1Protocol`: Exposes the `kuksa.val.v1` gRPC interface (fetch, fetchNode, update, updateNode, subscribe×3, streamedUpdate).
  - `KuksaValV2Protocol`: Exposes the `kuksa.val.v2` gRPC interface (fetchValue, fetchValues, publishValue, actuate, batchActuate, subscribeById, subscribe, openProviderStream, listMetadata, fetchServerInfo).
  - `VssNode` / `VssSignal<T>`: Generated Kotlin data classes representing VSS branches and leaf signals.
  - `VSS Processor Plugin`: `org.eclipse.velocitas.vss-processor-plugin:0.1.2` — KSP-based plugin; reads VSS YAML → emits `VssNode`/`VssSignal` Kotlin sources.
  - `Actuator`: VSS signal with `type == "actuator"`; only v2 `actuate()` / `batchActuate()` applicable.
  - `Sensor`: VSS signal with `type == "sensor"`; supports fetch, update/publish, and subscribe.
  - `MockProvider`: Standalone Kotlin JVM process (`:mock-provider` module) that connects to the databroker via `openProviderStream` (v2) and implements **BehaviorRules** that translate actuate requests into sensor value updates.
  - `BehaviorRule`: YAML-configured mapping `ActuatorSignal → SensorSignal` with a transform (direct / ramp / toggle) and `delay_ms`.
  - `DataBrokerViewModel`: Pure Kotlin class (no Android/JavaFX-framework coupling) that holds `StateFlow` properties for connection state, log entries, and active subscriptions. JavaFX controllers observe it via `kotlinx-coroutines-javafx`.

### 2.1 External Framework & Technology Research Matrix

| Target Ecosystem | Required Protocol / API | Auth & Transport | Constraints | SDK / Library |
| :--- | :--- | :--- | :--- | :--- |
| KUKSA Databroker | gRPC (Protobuf), port 55556 | Optional JWT via gRPC metadata; plaintext or TLS | Long-lived streaming calls must run off JavaFX Application Thread | `org.eclipse.kuksa:kuksa-java-sdk:0.4.0` (project dep) |
| VSS Model Generator | KSP compile-time processor | None | Input: YAML; Output: Kotlin data classes | `org.eclipse.velocitas.vss-processor-plugin:0.1.2` |
| JavaFX 21 LTS | Desktop UI framework | — | Must run on Java 21+; JavaFX Gradle plugin required; KSP generates sources before compilation | `org.openjfx.javafxplugin:0.1.0`; modules: `javafx.controls` |
| gRPC transport (JVM) | Netty-shaded gRPC | — | Use `grpc-netty-shaded` for JVM (not grpc-okhttp which is Android-only) | `io.grpc:grpc-netty-shaded:1.65.1` (matches project `libs.versions.toml`) |
| Kotlin Coroutines (JavaFX) | Flow collection on FX thread | — | `Dispatchers.Main` is JavaFX Application Thread when `kotlinx-coroutines-javafx` on classpath | `org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.8.1` |
| Kotlin Coroutines (core) | Structured concurrency, Flow | — | Matches project version catalog | `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1` |
| kaml | YAML parsing for behavior-rules.yaml | None | Kotlinx Serialization backend | `com.charleskorn.kaml:kaml:0.61.0` |
| Docker Compose | KUKSA Databroker container | — | `ghcr.io/eclipse-kuksa/kuksa-databroker:main`; `--insecure --port 55556` | Docker Compose v2 |
| MockProvider (host JVM) | gRPC `openProviderStream` (v2) | Plaintext | Must register before any `actuate()` | `:mock-provider` module (project-internal) |

---

## 3. Architecture Topology and Workflows

### 3.0 Host-Side Mock Environment

```
┌──────────────────────────────── Host Machine ──────────────────────────────────────┐
│                                                                                     │
│   mock-environment/docker-compose.yml                                               │
│   ┌────────────────────────────────────────────────────┐                            │
│   │  kuksa-databroker container  (port 55556)           │                            │
│   │  ghcr.io/eclipse-kuksa/kuksa-databroker:main        │                            │
│   │  args: --insecure --port 55556                      │                            │
│   └───────────────────────┬────────────────────────────┘                            │
│                           │ gRPC (localhost:55556)                                  │
│   ┌───────────────────────▼────────────────────────────┐                            │
│   │  :mock-provider JVM process                         │                            │
│   │  openProviderStream → ProvideActuationRequest       │                            │
│   │  behavior-rules.yaml:                               │                            │
│   │    CruiseControl.SpeedSet → Speed  (ramp, 500ms)    │                            │
│   │    ABS.IsEnabled          → IsEngaged (direct, 0ms) │                            │
│   │    Lights.Beam.High.IsOn  → IsActive  (direct, 0ms) │                            │
│   │    Transmission.SelectedGear → CurrentGear (300ms)  │                            │
│   │    CruiseControl.IsActive → IsActive (toggle, 0ms)  │                            │
│   └────────────────────────────────────────────────────┘                            │
│                                                                                     │
│   ┌────────────────────────────────────────────────────┐                            │
│   │  :java-test-app  (JavaFX desktop window)            │                            │
│   │  ManagedChannelBuilder (Netty) → localhost:55556    │                            │
│   └────────────────────────────────────────────────────┘                            │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

**Startup procedure:**
```bash
# 1. Start databroker
docker compose -f mock-environment/docker-compose.yml up -d

# 2. Start mock provider (separate terminal)
./gradlew :mock-provider:run

# 3. Launch test app
./gradlew :java-test-app:run
# App connects via Connection tab: host=localhost, port=55556
```

**behavior-rules.yaml schema** (`mock-environment/behavior-rules.yaml`):
```yaml
rules:
  - actuator: "Vehicle.ADAS.CruiseControl.SpeedSet"
    sensor:   "Vehicle.Speed"
    delay_ms: 500
    transform: ramp        # direct | ramp | toggle

  - actuator: "Vehicle.ADAS.ABS.IsEnabled"
    sensor:   "Vehicle.ADAS.ABS.IsEngaged"
    delay_ms: 0
    transform: direct

  - actuator: "Vehicle.Body.Lights.Beam.High.IsOn"
    sensor:   "Vehicle.Body.Lights.Beam.High.IsActive"
    delay_ms: 0
    transform: direct

  - actuator: "Vehicle.Powertrain.Transmission.SelectedGear"
    sensor:   "Vehicle.Powertrain.Transmission.CurrentGear"
    delay_ms: 300
    transform: direct

  - actuator: "Vehicle.ADAS.CruiseControl.IsActive"
    sensor:   "Vehicle.ADAS.CruiseControl.IsActive"
    delay_ms: 0
    transform: toggle
```

**Transform types:**
- `direct` — copies actuator target value to sensor after `delay_ms`.
- `ramp` — fetches current sensor value, linearly interpolates 10 steps to target over `delay_ms`; publishes each step individually so subscriptions fire.
- `toggle` — inverts current boolean sensor value regardless of the target value.

### 3.1 Component Interaction Matrix

| Source Component | Target / Subsystem | Interaction Type | Protocol / Data Contract | Description |
| :--- | :--- | :--- | :--- | :--- |
| `ConnectionController (JavaFX)` | `DataBrokerViewModel` | `StateFlow` observe / UI event | Kotlin coroutines + `kotlinx-coroutines-javafx` | User fills host/port/JWT/API-version; triggers `viewModel.connect()` |
| `DataBrokerViewModel` | `DataBrokerConnector` | `suspend` call | gRPC / Netty | Builds `ManagedChannel`, calls `connector.connect()` in `CoroutineScope` |
| `DataBrokerViewModel` | `KuksaValV1Protocol` | `suspend` / callback | Protobuf v1 | Dispatches all 8 v1 API calls |
| `DataBrokerViewModel` | `KuksaValV2Protocol` | `suspend` / `Flow` | Protobuf v2 | Dispatches all 10 v2 API calls |
| `SignalController (JavaFX)` | `DataBrokerViewModel` | `StateFlow` observe | Kotlin `StateFlow` | Displays generated VSS signal list; forwards user actions |
| `ActuatorController (JavaFX)` | `DataBrokerViewModel` | UI event + `StateFlow` | — | Issues actuate/batchActuate; shows response in log |
| `SubscriptionController (JavaFX)` | `DataBrokerViewModel` | `StateFlow<List<LogEntry>>` | — | Shows live subscription updates |
| `LogController (JavaFX)` | `DataBrokerViewModel` | `StateFlow<List<LogEntry>>` | — | Full audit log; Server Info button |
| `MockProvider` | `KuksaValV2Protocol.openProviderStream` | Bidirectional gRPC stream | `OpenProviderStreamRequest/Response` | Registers actuator ownership; receives `BatchActuateStreamRequest` |
| `BehaviorRuleEngine` | `KuksaValV2Protocol.publishValue` | `suspend` call | `PublishValueRequestV2` | Pushes computed sensor value back after `delay_ms` |
| `VSS Processor Plugin` | Kotlin source generator | KSP compile-time | VSS YAML → Kotlin | Emits VSS data classes from `vss/vss_rel_4.1.yaml` |

### 3.2 Primary Feature Workflows

#### Workflow 1: Connect to Databroker
1. **Trigger:** User fills host/port/JWT/API-version in Connection tab; clicks **Connect**.
2. **Validation:** ViewModel checks host non-empty, port in 1–65535; emits `ValidationError` if not.
3. **Channel:** `ManagedChannelBuilder.forAddress(host, port).usePlaintext()` (or `.useTransportSecurity()`) builds `ManagedChannel`.
4. **Connect:** `DataBrokerConnector(channel, jwt).connect()` in `viewModelScope`; state → `CONNECTING`.
5. **Success:** `DataBrokerConnection` stored; state → `CONNECTED`; non-connection tabs enabled.
6. **Failure:** `DataBrokerException` caught; state → `ERROR`; error message shown in Connection tab.

#### Workflow 2: Switch API Version (v1 ↔ v2)
1. **Trigger:** User clicks v1/v2 toggle button in Connection tab while connected.
2. Active subscription jobs cancelled; `connection.disconnect()` called.
3. ViewModel updates `activeApi`; re-connects with same config.
4. Actuators tab is shown for both v1 and v2 (v1 uses `ACTUATOR_TARGET` field; v2 uses `actuate()`).

#### Workflow 3: v1 — Fetch / Update / Subscribe
1. **Fetch path:** `kuksaValV1.fetch(FetchRequest(vssPath, field))` → `GetResponse` logged.
2. **Fetch node:** `kuksaValV1.fetch(VssNodeFetchRequest(vssNode))` → updated `VssNode` logged.
3. **Update path:** `kuksaValV1.update(UpdateRequest(vssPath, dataPoint, field))` → `SetResponse` logged.
4. **Update node:** `kuksaValV1.update(VssNodeUpdateRequest(vssNode))` → `VssNodeUpdateResponse` logged.
5. **Subscribe (listener):** `kuksaValV1.subscribe(SubscribeRequest(vssPath), VssPathListener)` — listener callbacks appended to live log.
6. **Subscribe (flow):** `kuksaValV1.subscribe(SubscribeRequest(vssPath))` → `Flow` collected in named `Job`.
7. **Subscribe (node):** `kuksaValV1.subscribe(VssNodeSubscribeRequest(vssNode), VssNodeListener)`.
8. **Streamed Update:** `kuksaValV1.streamedUpdate(receiverObserver)` → `senderStream` returned; user sends values via stream; responses logged.

#### Workflow 4: v2 — Fetch / Publish / Actuate / Stream
1. **fetchValue:** `kuksaValV2.fetchValue(FetchValueRequestV2(signalId))` → `GetValueResponse` logged.
2. **fetchValues:** `kuksaValV2.fetchValues(FetchValuesRequestV2(signalIds))` → `GetValuesResponse` logged (bulk fetch from signal list).
3. **publishValue:** `kuksaValV2.publishValue(PublishValueRequestV2(signalId, datapoint))` → `PublishValueResponse` logged.
4. **actuate:** `kuksaValV2.actuate(ActuateRequestV2(signalId, value))` → `ActuateResponse` logged.
5. **batchActuate:** `kuksaValV2.batchActuate(BatchActuateRequestV2(signalIds, value))` → `BatchActuateResponse` logged.
6. **subscribe (path):** `kuksaValV2.subscribe(SubscribeRequestV2(paths, bufferSize))` → `Flow` collected in named `Job`.
7. **subscribeById:** `kuksaValV2.subscribeById(SubscribeByIdRequestV2(ids))` → `Flow` collected.
8. **openProviderStream:** `kuksaValV2.openProviderStream(observer)` → returns `senderStream`; UI exposes Publish / Provide Actuation / Close buttons.
9. **listMetadata:** `kuksaValV2.listMetadata(ListMetadataRequestV2(root, filter))` → shown in dialog.
10. **fetchServerInfo:** `kuksaValV2.fetchServerInfo()` → shown in dialog.

#### Workflow 5: MockProvider Actuate Feedback Loop
1. MockProvider registers all actuator paths from `behavior-rules.yaml` via `ProvideActuationRequest`.
2. App calls `actuate(CruiseControl.SpeedSet, 120.0)` → Databroker routes `BatchActuateStreamRequest` to MockProvider.
3. `BehaviorRuleEngine` resolves rule (ramp, 500ms); fetches current `Vehicle.Speed`; computes 10 steps.
4. MockProvider publishes each step via `publishValue`; app subscriptions on `Vehicle.Speed` receive all 10 emissions.
5. MockProvider sends `BatchActuateStreamResponse` (empty errors); broker returns `ActuateResponse OK` to app.

#### Workflow 6: Error / Degraded State
1. Any API call throws `DataBrokerException` or `io.grpc.StatusException`.
2. ViewModel emits `ErrorEvent(grpcCode, message)` → logged as `Direction.ERROR` entry.
3. If code is `UNAVAILABLE` from actuate → log shows "No provider: run :mock-provider".
4. `DisconnectListener.onDisconnect()` → connection state → `DISCONNECTED`; tabs disabled.

---

## 4. Structural, UI/UX and Behavioral Requirements

### 4.1 Entry Points and Triggers
- **Surface Area:** JavaFX `Stage` with `TabPane`; launched via `./gradlew :java-test-app:run`.
- **Preconditions:** JDK 21+, network access to databroker host.
- **Tab Navigation:**
  - **Connection** (always enabled) — host/port/JWT/version config + connect/disconnect.
  - **Signals** (enabled when connected) — signal browser + detail panel; switchable between VSS-Model mode and Free-form Path mode.
  - **Actuators** (enabled when connected) — actuator list + single/batch actuate + provider stream.
  - **Subscriptions** (enabled when connected) — active subscriptions + live log.
  - **Log** (enabled when connected) — full audit log + server info.

### 4.1.1 Signal Input Modes

The Signals and Actuators tabs each support two mutually exclusive input modes, selectable via a **ToggleButton** pair in the tab toolbar:

| Mode | Label | Source of VSS Path | Use Case |
| :--- | :--- | :--- | :--- |
| **VSS Model** | `VSS Model` | Generated `VssNode`/`VssSignal` class hierarchy from `vss_rel_4.1.yaml` | Test with compile-time-typed signals; validates VssNode API surface |
| **Free-form Path** | `Raw Path` | Developer types any VSS path string directly into a `TextField` | Test arbitrary / custom signals; use path-based SDK APIs without generated classes; test signals from a different VSS version |

**VSS Model mode** (default):
- Left pane shows `ListView<SignalItem>` from `VssSignalRepository.allSignals()`.
- Selecting a row populates the detail panel with all metadata and the `VssNode` reference.
- Operation buttons invoke both node-based (`VssNodeFetchRequest`, `VssNodeUpdateRequest`, `VssNodeSubscribeRequest`) **and** path-based (`FetchRequest`, `UpdateRequest`, `SubscribeRequest`) variants so all SDK overloads are reachable.

**Free-form Path mode (Raw Path)**:
- Left pane is replaced by a single `TextField` labelled **VSS Path** (e.g., `Vehicle.Speed`).
- A **DataType** `ComboBox` (boolean, float, double, int32, uint32, int64, uint64, string) sets how the entered value is serialised into a `Datapoint` / `Types.Value`.
- All operation buttons invoke **only** the path-based SDK overloads:
  - v1: `FetchRequest(vssPath)`, `UpdateRequest(vssPath, dataPoint, field)`, `SubscribeRequest(vssPath)`.
  - v2: `FetchValueRequestV2(SignalID.path)`, `PublishValueRequestV2(SignalID.path, datapoint)`, `SubscribeRequestV2(listOf(path))`, `ActuateRequestV2(SignalID.path, value)`.
- No `VssNode` objects are constructed; this exercises the raw-path SDK surface end-to-end.
- A **History** `ComboBox` keeps the last 20 entered paths for quick recall (stored in-memory for the session).

**Invariant-6:** The active signal input mode is per-tab state; switching between VSS Model and Raw Path does not disconnect, does not cancel subscriptions, and does not clear the log.

### 4.2 Visual UI Wireframe & Layout Blueprint
- **Visual Mockup File:** `specs/drafts/java-test-app.mockup.html` (7 screens; open in browser)

#### Layout Zone Specification

| Zone ID | Layout Region | Contained Components | Notes |
| :--- | :--- | :--- | :--- |
| `ZONE-TITLEBAR` | Window title bar | App name, connection status badge | JavaFX `Stage.title` + custom CSS badge label |
| `ZONE-TABS` | `TabPane` (full window) | Connection / Signals / Actuators / Subscriptions / Log | Tabs disabled via `tab.isDisable` when disconnected |
| `ZONE-CONNECTION` | Connection tab content | Host `TextField`, Port `TextField`, JWT `TextField`, TLS `CheckBox`, API version `ToggleButton` pair, Connect/Disconnect `Button`, status `Label` | Single `VBox` layout |
| `ZONE-SIGNAL-MODE-BAR` | Signals tab toolbar | `ToggleButton` pair: **VSS Model** / **Raw Path**; DataType `ComboBox` (visible in Raw Path mode only) | Full-width toolbar below tab header |
| `ZONE-SIGNAL-SPLIT` | Signals tab — `SplitPane` | **VSS Model mode:** Left = signal `ListView` + filter `TextField` + type-filter buttons; **Raw Path mode:** Left = VSS Path `TextField` + History `ComboBox` + DataType `ComboBox`; Right = detail `VBox` (shared) | Split ratio 30/70; left pane content swaps on mode change |
| `ZONE-SIGNAL-DETAIL` | Right pane of signal split | Metadata grid (VSS Model only), value `TextField`, field selector (v1), operation buttons (all show in both modes; node-based buttons hidden in Raw Path mode), mini-log `TextArea` | Scrollable `VBox` |
| `ZONE-ACTUATOR-SPLIT` | Actuators tab — `SplitPane` | **VSS Model mode:** Left = actuator `ListView` with `CheckBox` per row; **Raw Path mode:** Left = Path `TextField` + DataType `ComboBox`; Right = actuate controls (shared) | Split ratio 30/70 |
| `ZONE-SUBSCRIPTIONS` | Subscriptions tab — `SplitPane` | Left: active subscription list + subscribe-by-path/id controls; Right: live log `TextArea` | Split ratio 35/65 |
| `ZONE-LOG` | Log tab — `TextArea` (full) | Full audit log; Clear + Export + Server Info buttons in toolbar | Monospaced font, auto-scroll |

### 4.3 Component State Matrix

| Component | Default (Disconnected) | Connecting | Connected | Error |
| :--- | :--- | :--- | :--- | :--- |
| `ConnectButton` | Enabled | Disabled + spinner label | Hidden / replaced by Disconnect | Enabled (Retry) |
| `Signals/Actuators/Subscriptions/Log tabs` | Disabled | Disabled | Enabled | Disabled |
| `API version toggle` | Enabled | Disabled | Enabled | Enabled |
| `Operation buttons` (Fetch/Update/Actuate) | N/A | N/A | Enabled | N/A |
| `Node-based operation buttons` (Fetch Node, Update Node, Subscribe Node) | N/A | N/A | Enabled in VSS Model mode; hidden in Raw Path mode | N/A |
| `VSS Path TextField` (Raw Path mode) | N/A | N/A | Editable; red border when empty on action | N/A |
| `History ComboBox` (Raw Path mode) | N/A | N/A | Populated with last 20 entries | N/A |
| `DataType ComboBox` (Raw Path mode) | N/A | N/A | Required; defaults to `float` | N/A |
| `Status label (Connection tab)` | "Waiting…" | "Connecting to host:port…" | "Connected · v1 · host:port" | "Error: \<message\>" |
| `Mini-log TextArea` | Empty | — | Appends entries | Red-tinted error entry |

### 4.4 Complete API Surface Coverage

#### v1 API Methods — all must have UI triggers

| API Method | UI Trigger | Request Type | Response Displayed |
| :--- | :--- | :--- | :--- |
| `fetch(FetchRequest)` | Signals detail → **Fetch** button | `FetchRequest(vssPath, field)` | `GetResponse.entriesList` in mini-log |
| `fetch(VssNodeFetchRequest)` | Signals detail → **Fetch (Node)** button | `VssNodeFetchRequest(vssNode)` | Updated `VssNode.toString()` |
| `update(UpdateRequest)` | Signals detail → **Update** button | `UpdateRequest(vssPath, dataPoint, field)` | `SetResponse.errorsList` or "OK" |
| `update(VssNodeUpdateRequest)` | Signals detail → **Update (Node)** button | `VssNodeUpdateRequest(vssNode)` | `VssNodeUpdateResponse` |
| `subscribe(SubscribeRequest, VssPathListener)` | Signals detail → **Subscribe (path)** toggle | `SubscribeRequest(vssPath)` | Listener callbacks in Subscriptions tab |
| `subscribe(SubscribeRequest) → Flow` | Signals detail → **Subscribe (flow)** toggle | `SubscribeRequest(vssPath)` | Flow emissions in Subscriptions tab |
| `subscribe(VssNodeSubscribeRequest, VssNodeListener)` | Signals detail → **Subscribe (node)** toggle | `VssNodeSubscribeRequest(vssNode)` | Node listener callbacks |
| `streamedUpdate(StreamObserver)` | Actuators tab → **Streamed Update ▶** button (v1) | Bidirectional stream | `StreamedUpdateResponse` in log |

#### v2 API Methods — all must have UI triggers

| API Method | UI Trigger | Request Type | Response Displayed |
| :--- | :--- | :--- | :--- |
| `fetchValue(FetchValueRequestV2)` | Signals detail → **Fetch Value** button | `FetchValueRequestV2(signalId)` | `GetValueResponse.dataPoint` |
| `fetchValues(FetchValuesRequestV2)` | Signals tab toolbar → **Fetch All** button | `FetchValuesRequestV2(signalIds)` | List of data points in log |
| `publishValue(PublishValueRequestV2)` | Signals detail → **Publish Value** button | `PublishValueRequestV2(signalId, datapoint)` | `PublishValueResponse` |
| `actuate(ActuateRequestV2)` | Actuators detail → **Actuate** button | `ActuateRequestV2(signalId, value)` | `ActuateResponse` |
| `batchActuate(BatchActuateRequestV2)` | Actuators tab → **Batch Actuate** button | `BatchActuateRequestV2(signalIds, value)` | `BatchActuateResponse` |
| `subscribe(SubscribeRequestV2) → Flow` | Subscriptions tab → **+ by Path** button | `SubscribeRequestV2(paths, bufferSize)` | Flow emissions in live log |
| `subscribeById(SubscribeByIdRequestV2) → Flow` | Subscriptions tab → **+ by ID** button | `SubscribeByIdRequestV2(signalIds)` | Flow emissions in live log |
| `openProviderStream(StreamObserver)` | Actuators tab → **Open Stream** button | Bidirectional stream | Stream responses in log |
| `listMetadata(ListMetadataRequestV2)` | Signals detail context menu → **Metadata** | `ListMetadataRequestV2(root, filter)` | Modal dialog with metadata list |
| `fetchServerInfo()` | Log tab toolbar → **Server Info** button | — | Modal dialog: name + version |

### 4.5 Data Schema

```kotlin
data class ConnectionConfig(
    val host: String,           // default "localhost"
    val port: Int,              // default 55556; valid range 1–65535
    val jwt: String?,           // null = no auth
    val useTls: Boolean,        // default false
    val apiVersion: ApiVersion,
)

enum class ApiVersion { V1, V2 }
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class LogEntry(
    val timestamp: Long,        // System.currentTimeMillis()
    val direction: Direction,
    val apiMethod: String,
    val payload: String,        // request/response proto .toString()
)

enum class Direction { REQUEST, RESPONSE, EVENT, ERROR }

data class SignalItem(
    val vssPath: String,
    val signalType: String,     // "sensor" | "actuator" | "attribute" | "branch"
    val dataType: String,
    val currentValue: String,
    val isSubscribed: Boolean,
)
```

---

## 5. Safety Governance and Autonomy Tiers
### 5.1 Permission Matrix
- **Tier 1 (Full Autonomy):** Read VSS YAML, run `./gradlew :java-test-app:run`, run unit tests.
- **Tier 2 (Human Review Required):** Write JavaFX UI code, push to remote.
- **Tier 3 (Restricted):** Modifying `:kuksa-java-sdk` library source; handling live vehicle ECU targets.

### 5.2 Degraded State and Failure Modes
- **Mode 1 (Databroker Unreachable):** `DataBrokerException("timeout")` → Connection tab shows error; Retry re-invokes `connect()`.
- **Mode 2 (gRPC Status Error):** `StatusException` (`NOT_FOUND`, `PERMISSION_DENIED`, etc.) → logged as ERROR entry; Snackbar-style `Label` in relevant tab.
- **Mode 3 (Invalid Value Input):** Value cannot be parsed to signal data type → `TextField` border turns red; API call not dispatched.
- **Mode 4 (No Actuator Provider):** `actuate()` returns `UNAVAILABLE` → log entry "No provider: run `./gradlew :mock-provider:run`".
- **Mode 5 (Subscription Backpressure):** v2 subscribe `bufferSize` exceeded → log warning; UI prompts resubscribe with larger buffer.

---

## 6. Synthetic Evaluation Cases

1. **Happy Path v1:** Connect localhost:55556; fetch `Vehicle.Speed` → `GetResponse` with float value within 2 s.
2. **Happy Path v2:** `publishValue(Vehicle.Speed, 60.0f)` then `fetchValue` → returned value equals 60.0.
3. **Actuate + ramp feedback:** `actuate(CruiseControl.SpeedSet, 120.0f)` → MockProvider publishes 10 intermediate `Vehicle.Speed` values; subscription log shows all 10 within 600 ms; final value = 120.0f.
4. **Subscribe v1 flow:** Subscribe `Vehicle.Speed`; externally update; `VssPathListener.onEntryChanged` fires within 3 s.
5. **Batch actuate:** Select 2 actuators; `batchActuate` → both respond OK; 2 sensor updates appear in live log.
6. **Error — no host:** Connect to non-existent host:port → `DataBrokerException: timeout` shown in Connection tab within 10 s.
7. **VssNode fetch:** `fetch(VssNodeFetchRequest(VssVehicle()))` → child signals populated from broker.
8. **Server info:** `fetchServerInfo()` → dialog shows non-empty `name` and `version`.
9. **openProviderStream:** Stream opened; `PublishValuesRequest` sent; no error in response stream.
10. **listMetadata:** `listMetadata("Vehicle.ADAS", "*")` → dialog shows ≥1 metadata entry.

---

## 7. Test Scenario and Use Case Matrix

### 7.1 Unit Test Scenarios (JVM, no JavaFX / Docker deps)

- **UT-1: ConnectionConfig host validation**
  - **Given:** `ConnectionConfig(host="", port=55556, ...)`
  - **When:** `ConfigValidator.validate(config)`
  - **Then:** `ValidationResult.Error("host must not be empty")`

- **UT-2: ConnectionConfig port boundary**
  - **Given:** `ConnectionConfig(host="x", port=0, ...)`
  - **When:** `ConfigValidator.validate(config)`
  - **Then:** `ValidationResult.Error("port must be in range 1–65535")`

- **UT-3: LogEntry creation from SetResponse**
  - **Given:** A `SetResponse` proto object
  - **When:** `LogEntry.fromResponse("update", setResponse)`
  - **Then:** `direction == RESPONSE`, `apiMethod == "update"`, payload non-empty

- **UT-4: SignalType detection**
  - **Given:** A generated VSS node with `type = "sensor"`
  - **When:** `SignalItem.fromVssNode(vssNode)`
  - **Then:** `signalType == "sensor"`

- **UT-5: BehaviorRuleEngine — direct transform**
  - **Given:** Rule `actuator="A", sensor="B", delay_ms=0, transform=DIRECT`
  - **When:** `engine.computeDirectValue(rule, Types.Value.newBuilder().setFloat(42f).build())`
  - **Then:** Returns `Types.Value` with `float == 42f`

- **UT-6: BehaviorRuleEngine — ramp produces 10 steps**
  - **Given:** Rule `transform=RAMP`; current=0f; target=100f; steps=10
  - **When:** `engine.computeRampSteps(rule, 0f, 100f, 10)`
  - **Then:** Returns list `[10f, 20f, …, 100f]` (size 10)

- **UT-7: BehaviorRuleEngine — toggle inverts boolean**
  - **Given:** Rule `transform=TOGGLE`; current value `bool=false`
  - **When:** `engine.computeToggle(rule, Types.Value.newBuilder().setBool(false).build())`
  - **Then:** Returns `Types.Value` with `bool == true`

### 7.2 Integration Test Scenarios (requires Docker Databroker + optional MockProvider)

- **IT-1: v1 update + fetch round-trip**
  - **Given:** Insecure Databroker on `localhost:<dynamic-port>` via `InsecureDataBrokerDockerContainer`
  - **When:** `update(UpdateRequest("Vehicle.Speed", Datapoint.float(80f)))` then `fetch(FetchRequest("Vehicle.Speed"))`
  - **Then:** `GetResponse.entriesList[0].value.float == 80f`

- **IT-2: v2 publishValue + fetchValue round-trip**
  - **Given:** Same Databroker
  - **When:** `publishValue(SignalID("Vehicle.Speed"), Value.float(60f))` then `fetchValue`
  - **Then:** `GetValueResponse.dataPoint.value.float == 60f`

- **IT-3: v2 subscribe receives published update**
  - **Given:** Active subscription on `Vehicle.Speed` via `SubscribeRequestV2`
  - **When:** `publishValue` sets speed to 42f
  - **Then:** Flow emits response with `entriesMap["Vehicle.Speed"].value.float == 42f` within 3000 ms

- **IT-4: v2 listMetadata returns entries**
  - **Given:** Databroker with default VSS
  - **When:** `listMetadata(ListMetadataRequestV2("Vehicle", "*"))`
  - **Then:** `ListMetadataResponse` has ≥10 entries

- **IT-5: v2 fetchServerInfo**
  - **Given:** Connected Databroker
  - **When:** `fetchServerInfo()`
  - **Then:** `name` and `version` fields are non-empty strings

- **IT-6: v1 VssNode fetch with generated class**
  - **Given:** Databroker; `update("Vehicle.Speed", float(55f))`
  - **When:** `kuksaValV1.fetch(VssNodeFetchRequest(VssVehicleSpeed()))` (generated class)
  - **Then:** Returned node value == 55f

- **IT-7M: MockProvider actuate + ramp feedback**
  - **Given:** Databroker running; MockProvider in-process with `CruiseControl.SpeedSet → Speed (ramp, 500ms)`; subscription active on `Vehicle.Speed`
  - **When:** `actuate(CruiseControl.SpeedSet, 120f)`
  - **Then:** `ActuateResponse` OK; ≥2 `Vehicle.Speed` emissions collected within 600 ms; final value == 120f

- **IT-8M: MockProvider actuate + toggle**
  - **Given:** MockProvider with `ABS.IsEnabled → ABS.IsEngaged (direct, 0ms)`
  - **When:** `actuate(ABS.IsEnabled, true)`
  - **Then:** `fetchValue(ABS.IsEngaged).dataPoint.value.bool == true` within 100 ms

- **IT-9M: Batch actuate + multiple sensor updates**
  - **Given:** Both rules loaded in MockProvider
  - **When:** `batchActuate([SpeedSet=100f, ABS.IsEnabled=true])`
  - **Then:** `BatchActuateResponse` OK; both `Vehicle.Speed` and `ABS.IsEngaged` updated within 600 ms

- **IT-10M: No provider → UNAVAILABLE**
  - **Given:** Databroker running; MockProvider NOT started
  - **When:** `actuate(CruiseControl.SpeedSet, 80f)`
  - **Then:** Throws `DataBrokerException` containing gRPC code `UNAVAILABLE`

### 7.3 UI Test Scenarios (TestFX)

- **UI-1: Connection tab renders on startup**
  - **Given:** App launched
  - **When:** Main window shown
  - **Then:** Connect button visible and enabled; Signals/Actuators/Subscriptions/Log tabs disabled

- **UI-2: Connecting state — connect button disabled**
  - **Given:** ViewModel emits `ConnectionState.CONNECTING`
  - **When:** Connection tab re-renders
  - **Then:** Connect button disabled; status label contains "Connecting"

- **UI-3: Error state — error message visible + retry enabled**
  - **Given:** ViewModel emits `ConnectionState.ERROR` with message "timeout"
  - **When:** Connection tab re-renders
  - **Then:** Status label contains "Error"; Connect button re-enabled

- **UI-4: API version switch enables correct operation buttons**
  - **Given:** Connected state; v1 active
  - **When:** User clicks "kuksa.val.v2" toggle
  - **Then:** v2 operation buttons (Fetch Value, Publish Value, Actuate) become visible in Signals detail

- **UI-5: Switching to Raw Path mode hides node-based buttons**
  - **Given:** Connected; Signals tab; VSS Model mode active
  - **When:** User clicks "Raw Path" toggle
  - **Then:** Signal `ListView` replaced by VSS Path `TextField` + DataType `ComboBox`; "Fetch (Node)", "Update (Node)", "Subscribe (node)" buttons are hidden; path-based buttons remain visible

- **UI-6: Raw Path mode — empty path triggers validation**
  - **Given:** Raw Path mode active; VSS Path `TextField` is empty
  - **When:** User clicks **Fetch** (v1) or **Fetch Value** (v2)
  - **Then:** `TextField` border turns red; error label "VSS path must not be empty" shown; ViewModel NOT called

- **UI-7: Raw Path mode — fetch via path string**
  - **Given:** Raw Path mode; path = "Vehicle.Speed"; DataType = float; v1 active
  - **When:** User clicks **Fetch**
  - **Then:** `viewModel.fetchV1("Vehicle.Speed", Field.VALUE)` called (mocked); log entry appears with `apiMethod == "fetch"`

- **UI-8: History ComboBox records last entered path**
  - **Given:** Raw Path mode; user entered "Vehicle.Speed" and clicked Fetch
  - **When:** User opens History `ComboBox`
  - **Then:** "Vehicle.Speed" appears as first entry in dropdown

---

### 7.4 Additional Unit Tests — Free-form Path Mode

- **UT-8: DataTypeSerializer — float path**
  - **Given:** `dataType = "float"`, `value = "95.5"`
  - **When:** `DataTypeSerializer.toDatapoint(dataType, value)` (v1)
  - **Then:** Returns `Datapoint` with `float == 95.5f`

- **UT-9: DataTypeSerializer — boolean path**
  - **Given:** `dataType = "boolean"`, `value = "true"`
  - **When:** `DataTypeSerializer.toDatapoint(dataType, value)`
  - **Then:** Returns `Datapoint` with `bool == true`

- **UT-10: DataTypeSerializer — invalid value for type**
  - **Given:** `dataType = "float"`, `value = "notANumber"`
  - **When:** `DataTypeSerializer.toDatapoint(dataType, value)`
  - **Then:** Throws `DataTypeSerializationException("Cannot parse 'notANumber' as float")`

- **UT-11: PathHistory — stores last 20, evicts oldest**
  - **Given:** `PathHistory` with 20 already-entered paths
  - **When:** `history.record("Vehicle.NewSignal")`
  - **Then:** History size == 20; "Vehicle.NewSignal" is first; oldest entry evicted

---
