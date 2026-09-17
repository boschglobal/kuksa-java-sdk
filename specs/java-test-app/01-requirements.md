# Specification: Kuksa Java SDK Java Test App & Mock Provider

- **Type:** FEATURE
- **ID:** FR-JAVA-TEST-APP
- **Branch:** feature/sdd-java-test-app
- **Associated ADR:** specs/adr/0001-java-test-app-javafx-and-mock-provider.md

---

## 1. Problem Statement & Goal
Provide a self-contained JavaFX desktop application (`:java-test-app`) and standalone Mock Provider (`:mock-provider`) to exercise and verify all public methods of `KuksaValV1Protocol` and `KuksaValV2Protocol` with both compile-time VSS models and raw free-form path inputs.

### Out of Scope
- Android-specific bindings or APK generation.
- Production ECU deployment.

---

## 2. Actors & Cockburn Use Cases
- **Primary Actor:** SDK Developer / Tester.
- **Secondary Actor:** Automated Test Harness.
- **Main Success Scenarios:**
  1. **Connect:** User configures host/port/JWT/version and connects to KUKSA Databroker.
  2. **V1/V2 Signal Interaction:** User browses generated VSS signals or enters raw paths to fetch, update, actuate, or subscribe.
  3. **Mock Actuation Loop:** User issues actuation request; `:mock-provider` receives request, applies behavior rule (direct/ramp/toggle), and publishes sensor updates.
  4. **Log & Inspection:** User views live event/error log and fetches server info/metadata.

---

## 3. Requirements (MoSCoW & Gherkin)

### MUST Requirements
- **FR-001 (Connection & Protocol Switching):**
  - *Given* a running KUKSA Databroker on host:port,
  - *When* the user inputs valid connection details and clicks Connect,
  - *Then* the application establishes a `DataBrokerConnection` and enables all operational tabs.
- **FR-002 (V1 Protocol API Surface):**
  - *Given* an active V1 connection,
  - *When* invoking fetch, fetchNode, update, updateNode, subscribe (path/node/flow), or streamedUpdate,
  - *Then* the request and response are executed and appended to the log.
- **FR-003 (V2 Protocol API Surface):**
  - *Given* an active V2 connection,
  - *When* invoking fetchValue, fetchValues, publishValue, actuate, batchActuate, subscribe (path/id), openProviderStream, listMetadata, or fetchServerInfo,
  - *Then* the request and response are executed and appended to the log.
- **FR-004 (Dual Input Modes: VSS Model & Raw Path):**
  - *Given* the Signals or Actuators tab,
  - *When* switching between VSS Model and Raw Path modes,
  - *Then* the UI toggles between generated signal tree/list and raw text input with DataType selector and history without disconnecting.
- **FR-005 (Mock Provider & Behavior Rules):**
  - *Given* a running `:mock-provider` with configured behavior rules,
  - *When* an actuation request is sent for a registered actuator,
  - *Then* the provider executes the rule (direct, ramp, toggle) and updates corresponding sensors.

### Non-Functional Requirements (NFR)
- **NFR-001 (Concurrency):** Asynchronous operations and gRPC streams must never block the JavaFX Application Thread.
- **NFR-002 (Test Coverage):** Minimum 90% line / 85% branch coverage on core domain, ViewModel, serializers, and rule engine.
