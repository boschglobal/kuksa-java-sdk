# Feature Specification: Generic Actuator Mock Provider

## 1. Context and Architectural Intent
- **Feature Identifier:** SPEC-MOCK-PROVIDER-002
- **Target Persona / Client:** Automotive software developer, test automation engineer, or UI developer using the `kuksa-java-sdk` to actuate VSS signals against a local or remote KUKSA Databroker using either `kuksa.val.v2` or `kuksa.val.v1`.
- **Core Intent:** Provide an out-of-the-box, zero-configuration generic Actuator Mock Provider that automatically claims all known VSS actuator signals dynamically from the `vss/` folder, supports both `kuksa.val.v2` (bidirectional `openProviderStream`) and `kuksa.val.v1` (`FIELD_ACTUATOR_TARGET` reflector), immediately forwards/publishes the actuated values back to the Databroker, and logs informative telemetry confirming the successful value update.
- **Problem Justification:** When invoking `actuate()` on any VSS actuator (e.g., `Vehicle.ADAS.CruiseControl.SpeedSet`) without an explicitly matched behavior rule, the current MockProvider does not claim dynamic/unlisted signals or may fail to properly acknowledge and forward the actuated state. Furthermore, developers need seamless actuation support across both KUKSA API versions (v1 target reflection and v2 stream actuation) and across VSS versions (v4.2, v6.0+) dynamically without recompilation.

---

## 2. System Domain and Workspace Alignment
- **Impacted Systems:** Sub-module `:mock-provider`, class `MockProvider.kt`, discovery class `VssActuatorDiscovery.kt`, new parser `VssDefinitionParser.kt`, engine `BehaviorRuleEngine.kt`, and CLI entry point `Main.kt`. Zero breaking changes to `:kuksa-java-sdk` or `:vss-core`.
- **Domain Invariants:**
  - `Invariant-1`: Actuator paths must be dynamically loaded from VSS definition files in the `vss/` directory (`.yaml` and/or `.json`, e.g., `vss_rel_4.2.yaml`, `vss_rel_6.0.json`), ensuring compatibility across VSS releases without recompilation.
  - `Invariant-2`: On `kuksa.val.v2`, every discoverable actuator path must be claimed via `ProvideActuationRequest` on startup; incoming `BatchActuateStreamRequest`s must be acknowledged with `BatchActuateStreamResponse(signalId)` and forwarded via `publishValue()`.
  - `Invariant-3`: On `kuksa.val.v1`, the provider must subscribe to `FIELD_ACTUATOR_TARGET` on `Vehicle` and immediately reflect target datapoints to `FIELD_VALUE` via `update()`.
  - `Invariant-4`: In generic passthrough mode (unmatched by YAML rules), the provider must directly set the actuated value for the target signal path.
  - `Invariant-5`: Custom behavior rules configured in `behavior-rules.yaml` (such as custom sensor target mappings, step ramps, or toggles) retain priority over generic passthrough for both v1 and v2.
  - `Invariant-6`: The provider logs structured terminal feedback indicating actuation request ingestion, stream acknowledgment, value publication, and completion status.
  - `Invariant-7`: When started with `--web-port <N>`, the provider exposes a live HTTP monitoring dashboard on the specified port. When the flag is absent, the provider runs in headless mode with identical behavior to prior versions.

- **Domain Lexicon:**
  - `kuksa.val.v2 Actuation`: Bi-directional gRPC streaming RPC `openProviderStream` where provider claims signals with `ProvideActuationRequest`, receives `BatchActuateStreamRequest`, returns `BatchActuateStreamResponse`, and publishes with `publishValue`.
  - `kuksa.val.v1 Actuation`: Target-reflection model where client sets `FIELD_ACTUATOR_TARGET`, and provider subscribes to target updates and updates `FIELD_VALUE`.
  - `Generic Passthrough`: The default fallback behavior wherein any actuated value is directly published/reflected back to the Databroker for the target signal path.
  - `BehaviorRule`: An optional YAML-configured rule defining a custom target sensor, delay, or value transformation (Direct, Ramp, Toggle).
  - `Web Dashboard`: Optional embedded HTTP server (`MockProviderWebServer`) exposing a live monitoring UI at `http://<host>:<web-port>`. Serves real-time actuation telemetry via SSE, claimed actuator registry, and runtime stats.
  - `DashboardState`: Thread-safe shared state object propagating log entries, actuation counters, and actuator registry to dashboard SSE subscribers.
  - `Headless Mode`: Default operating mode when `--web-port` is absent; behavior is identical to pre-dashboard versions.

### 2.1 External Framework & Technology Research Matrix

| Target Ecosystem / Device | Required Protocol / API | Auth & Transport | Rate Limits & Constraints | Recommended SDK / Library |
| :--- | :--- | :--- | :--- | :--- |
| KUKSA Databroker v2 | `kuksa.val.v2.VAL` gRPC | Plaintext / TLS, Optional JWT Metadata | Provider stream must remain open continuously; batch actuations require per-signal acknowledgment | `kuksa-java-sdk` (`KuksaValV2Protocol`, `DataBrokerConnector`) |
| KUKSA Databroker v1 | `kuksa.val.v1.VAL` gRPC | Plaintext / TLS, Optional JWT Metadata | Target reflector subscribes to `FIELD_ACTUATOR_TARGET` on `Vehicle` subtree | `kuksa-java-sdk` (`KuksaValV1Protocol`) |
| Dynamic VSS Parser | JSON & YAML file parsers | Local Filesystem (`vss/`) | Supports flat YAML keys and nested JSON trees | `com.charleskorn.kaml`, `kotlinx.serialization.json` |
| CLI / Engine Runtime | Kotlin Coroutines & SupervisorJob | In-process JVM | Dispatchers.Default for asynchronous delayed steps and batch forwarding | `kotlinx.coroutines:kotlinx-coroutines-core:1.8.1` |

---

## 3. Architecture Topology and Workflows

### 3.1 Component Interaction Matrix

| Source Component | Target / Subsystem | Interaction Type | Protocol / Data Contract | Description |
| :--- | :--- | :--- | :--- | :--- |
| `MockProvider.Main` | `VssDefinitionParser` | Local In-Process | JVM Method Call | Dynamically parses `vss/` YAML/JSON files for all actuator paths |
| `MockProvider` | `KUKSA Databroker v2` | gRPC Stream (Open) | `OpenProviderStreamRequest` | Claims all actuators via `ProvideActuationRequest` |
| `MockProvider` | `KUKSA Databroker v1` | gRPC Subscription | `SubscribeRequest(FIELD_ACTUATOR_TARGET)` | Subscribes to actuator targets across `Vehicle` subtree |
| `Client (v2)` | `KUKSA Databroker v2` | gRPC Unary | `ActuateRequest` / `BatchActuateRequest` | Client requests actuator value change |
| `KUKSA Databroker v2` | `MockProvider` | gRPC Stream (Downlink) | `BatchActuateStreamRequest` | Databroker dispatches v2 actuation to registered provider |
| `MockProvider` | `KUKSA Databroker v2` | gRPC Stream (Uplink) | `BatchActuateStreamResponse` | Provider acknowledges v2 actuation |
| `MockProvider` | `KUKSA Databroker v2` | gRPC Unary (`PublishValue`) | `PublishValueRequestV2` | Updates actual signal value in Databroker |
| `Client (v1)` | `KUKSA Databroker v1` | gRPC Unary | `UpdateRequest(FIELD_ACTUATOR_TARGET)` | Client sets v1 actuator target |
| `MockProvider` | `KUKSA Databroker v1` | gRPC Unary (`Update`) | `UpdateRequest(FIELD_VALUE)` | Reflects target to actual value in Databroker |

### 3.2 Primary Feature Workflows

#### Workflow 1: Kuksa Val V2 Stream Actuation & Auto-Forward (Happy Path)
1. **Trigger:** Client invokes `actuate("Vehicle.ADAS.CruiseControl.SpeedSet", Value(float = 120.0f))` via `kuksa.val.v2.VAL/Actuate`.
2. **Step 1 (Databroker Provider Stream Routing):** Databroker identifies `MockProvider` as the registered provider (via prior `ProvideActuationRequest`) and pushes `BatchActuateStreamRequest` over the open gRPC stream.
3. **Step 2 (Stream Acknowledgment):** `MockProvider` creates a `BatchActuateStreamResponse(signalId = SpeedSet)` and sends it back to acknowledge receipt.
4. **Step 3 (Generic Passthrough & Set):** Finding no custom YAML rule, `MockProvider` immediately calls `conn.kuksaValV2.publishValue()` for `Vehicle.ADAS.CruiseControl.SpeedSet` with value `120.0f`.
5. **Step 4 (Telemetry & Logging):** System emits: `[v2 ACTUATE SUCCESS] Set Vehicle.ADAS.CruiseControl.SpeedSet -> 120.0`.

#### Workflow 2: Kuksa Val V1 Target Reflector & Auto-Forward (Happy Path)
1. **Trigger:** Client invokes `update("Vehicle.ADAS.CruiseControl.SpeedSet", Datapoint(float = 120.0f), FIELD_ACTUATOR_TARGET)` via `kuksa.val.v1.VAL/Update`.
2. **Step 1 (Target Subscription Event):** Databroker pushes entry update with `hasActuatorTarget() == true` on the provider's active `Vehicle` subscription.
3. **Step 2 (Generic Passthrough & Set):** `MockProvider` intercepts target datapoint and issues `UpdateRequest(vssPath = path, dataPoint = targetDatapoint, field = FIELD_VALUE)`.
4. **Step 3 (Telemetry & Logging):** System emits: `[v1 ACTUATE SUCCESS] Reflected Vehicle.ADAS.CruiseControl.SpeedSet target -> FIELD_VALUE (120.0)`.

#### Workflow 3: Custom BehaviorRule Interception (V1 & V2)
1. **Trigger:** Client actuates a signal matched by `behavior-rules.yaml` (e.g. `Vehicle.Body.Lights.IsHighBeamOn` -> `HighBeamIndicator` with delay `50ms` or ramp).
2. **Step 1 (Ingestion & Ack):** Provider acknowledges request (v2 stream ack or v1 event intake).
3. **Step 2 (Rule Match & Transform):** `BehaviorRuleEngine` resolves rule and applies configured delay, ramp steps, or sensor redirection.
4. **Step 3 (Publish Target):** Provider updates target sensor (`publishValue` for v2 / `update(FIELD_VALUE)` for v1).
5. **Step 4 (Log):** Provider logs rule execution and targeted sensor mutation.

#### Workflow 4: Dynamic Multi-Version VSS Discovery (`vss/` Folder)
1. **Trigger:** `MockProvider` starts up.
2. **Step 1 (Dynamic File Scanning):** Scans the `vss/` directory (or `--vss-dir` / `--vss-file` path) for candidate VSS definition files (e.g. `vss_rel_*.yaml`, `vss_rel_*.json`).
3. **Step 2 (Format-Agnostic Parsing):** Parses files dynamically:
   - For YAML files: Reads top-level signal mappings (e.g., `Vehicle.ADAS.CruiseControl.SpeedSet:` where `type == "actuator"`).
   - For JSON files: Recursively traverses node tree and nested `children` objects extracting signals with `"type": "actuator"`.
4. **Step 3 (Registration):** Issues `ProvideActuationRequest` on v2 and initializes v1 subscription on `Vehicle`.
5. **Step 4 (Log):** Emits total count of claimed actuators (e.g., "Registered 482 actuator paths discovered from vss/vss_rel_4.2.yaml for v2 actuation").

---

## 4. Structural, UI/UX and Behavioral Requirements

### 4.1 Entry Points and Triggers
- **Surface Area:** CLI execution via `./gradlew :mock-provider:run`, Docker (`mock-environment/docker-compose.yml`), or embedded instantiation in test harnesses.
- **CLI Flags:**
  - `--host <address>` (Default: `localhost`)
  - `--port <number>` (Default: `55556`)
  - `--rules <path>` (Default: `mock-environment/behavior-rules.yaml` — optional, starts with empty rules if missing)
  - `--vss-dir <path>` (Default: `vss` — directory containing `vss_rel_*.yaml` or `vss_rel_*.json` files)
  - `--vss-file <path>` (Optional specific VSS file path override)
  - `--generic` (Explicit flag enabling generic auto-forward for all unmapped signals; enabled by default)
  - `--verbose` / `-v` (Enables detailed trace logging for each actuate/ack/publish step)
  - `--web-port <number>` (Optional — enables embedded HTTP dashboard on the specified port; omitting this flag preserves headless mode)
- **Preconditions:** KUKSA Databroker running on target host and port.
- **Docker:** `mock-environment/docker-compose.yml` defines both `databroker` and `mock-provider` services. The `mock-provider` container exposes port `8080` for the dashboard; omit `--web-port` in the compose `command` to run headless.

### 4.2 Web Dashboard (UI/UX)
- **Implementation:** Embedded `MockProviderWebServer` (Java built-in `com.sun.net.httpserver`, no additional dependencies) started when `--web-port <N>` is provided.
- **Visual Mockup Reference:** `specs/drafts/generic-actuator-mock-provider.mockup.html`
- **Runtime Asset:** `mock-provider/src/main/resources/web/index.html` (served from the JAR classpath)
- **HTTP Endpoints:**

| Method | Path | Description |
| :--- | :--- | :--- |
| `GET` | `/` | Serves the live monitoring dashboard (`index.html`) |
| `GET` | `/api/state` | JSON snapshot: claimed signal count, actuations processed, custom rule count, host/port, stream status |
| `GET` | `/api/actuators?filter=` | Paginated actuator registry; `filter` param performs case-insensitive substring match on path |
| `GET` | `/api/logs/stream` | Server-Sent Events (SSE) stream of live log entries; replays last 500 entries on connect |
| `POST` | `/api/control/stop` | Triggers graceful provider shutdown (used by the Stop Provider button) |

- **Dashboard Sections:**
  - **Header:** Provider name, stream active badge (live), target Databroker address, Stop Provider button.
  - **Stat Cards:** Claimed Signals, Actuations Processed, Mode, Custom Rules count — polled every 3 s from `/api/state`.
  - **Terminal Stream:** Auto-scrolling SSE log terminal with structured actuation blocks (value, rule match, ack, forward result) and Clear button.
  - **Claimed Actuators Registry:** Filterable table showing path, handling strategy (Auto-Forward vs. named rule), and target signal.
  - **Sidebar:** Current configuration display (host, port, mode, discovery sources).

### 4.3 Component State Matrix

| Component Name | Default State | Loading State | Error State | Empty State |
| :--- | :--- | :--- | :--- | :--- |
| `v2 Provider Stream` | `STREAM ACTIVE` (gRPC connected) | `CONNECTING...` (Channel init) | `STREAM ERROR` (Reconnect backoff) | `DISCONNECTED` |
| `v1 Target Reflector` | `REFLECTOR ACTIVE` (Subscribed) | `SUBSCRIBING...` | `SUBSCRIPTION ERROR` | `INACTIVE` |
| `Signal Registry` | N signals dynamically loaded from `vss/` | Scanning `vss/` directory | Discovery fallback (`VssVehicle`) | 0 signals registered |
| `Web Dashboard` | Running on `--web-port` (if set) | — | SSE clients auto-reconnect | Headless (no `--web-port`) |

---

## 5. Safety Governance and Autonomy Tiers

### 5.1 Permission Matrix
- **Tier 1 (Full Autonomy):** Read VSS tree structure from filesystem, query Databroker metadata, register provider stream, publish sensor/actuator values to mock databroker.
- **Tier 2 (Human Review Required):** Modifications to default YAML behavior rules or production test suites.
- **Tier 3 (Restricted / High Risk):** Direct actuation of physical vehicle CAN buses or un-isolated production hardware.

### 5.2 Degraded State and Failure Modes
- **Mode 1 (Missing YAML File):** If `behavior-rules.yaml` does not exist, MockProvider proceeds in pure generic auto-forward mode.
- **Mode 2 (Missing VSS Directory):** If `vss/` folder has no files, falls back to compiled `VssVehicle` reflection.
- **Mode 3 (Databroker Disconnect):** Reconnect loop every 2s for both v1 reflector and v2 stream.

---

## 6. Synthetic Evaluation Cases (Evals)
1. **v2 Happy Path (SpeedSet Actuation):**
   - **Input:** `kuksaValV2.actuate("Vehicle.ADAS.CruiseControl.SpeedSet", float = 120.0f)`
   - **Expected Result:** `BatchActuateStreamResponse` returned; Databroker datapoint for `SpeedSet` updated to `120.0f`; log `[v2 ACTUATE SUCCESS]` emitted.
2. **v1 Happy Path (SpeedSet Target Reflection):**
   - **Input:** `kuksaValV1.update("Vehicle.ADAS.CruiseControl.SpeedSet", Datapoint(float = 120.0f), FIELD_ACTUATOR_TARGET)`
   - **Expected Result:** Target intercepted; `FIELD_VALUE` updated to `120.0f`; log `[v1 ACTUATE SUCCESS]` emitted.
3. **Dynamic VSS Swap (v4.2 -> v6.0):**
   - **Input:** Place `vss_rel_6.0.json` in `vss/`.
   - **Expected Result:** MockProvider loads new actuator paths dynamically without code recompilation.

---

## 7. Test Scenario and Use Case Matrix

### 7.1 Unit Test Scenarios (Domain & Logic Isolation)
- **Scenario UT-1: Generic Rule Resolution Fallback**
  - **Given:** Empty rule list.
  - **When:** `resolveRule("Vehicle.ADAS.CruiseControl.SpeedSet")` called.
  - **Then:** Returns `DIRECT` transform with target == actuator.

- **Scenario UT-2: Dynamic VSS File Discovery (YAML & JSON Across Releases)**
  - **Given:** `vss_rel_4.2.yaml` and `vss_rel_4.2.json`.
  - **When:** `VssDefinitionParser.parseActuators(file)` is executed on both formats.
  - **Then:** Both parser invocations extract valid actuator paths (such as `Vehicle.ADAS.CruiseControl.SpeedSet`).

- **Scenario UT-3: BatchActuateStreamResponse Construction**
  - **Given:** Incoming `ActuateRequest` for `"Vehicle.SpeedSet"`.
  - **When:** Response built.
  - **Then:** `BatchActuateStreamResponse.signalId.path == "Vehicle.SpeedSet"`.

### 7.2 Integration Test Scenarios (gRPC Stream & Databroker Contracts)
- **Scenario IT-1: End-to-End v2 Generic Actuate and Automatic Forwarding**
  - **Given:** Running Databroker and connected `MockProvider`.
  - **When:** Client issues `kuksaValV2.actuate("Vehicle.ADAS.CruiseControl.SpeedSet", 110.0f)`.
  - **Then:** Succeeded without error; `fetchValue` returns `110.0f`.

- **Scenario IT-2: End-to-End v1 Target Reflection and Auto-Forwarding**
  - **Given:** Running Databroker and connected `MockProvider`.
  - **When:** Client issues `kuksaValV1.update("Vehicle.ADAS.CruiseControl.SpeedSet", 110.0f, FIELD_ACTUATOR_TARGET)`.
  - **Then:** Reflected to `FIELD_VALUE`; `fetch` returns `110.0f`.
