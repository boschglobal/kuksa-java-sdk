# Implementation Plan: Generic Actuator Mock Provider

## Executive Overview
- **Associated Specification:** `specs/mock-provider/mock-provider.spec.md`
- **Architectural Pattern:** Event-Driven Provider Stream & Fallback Strategy Pattern (gRPC Bi-directional Streaming, V1 Subscription Reflector & Local Dynamic Passthrough Engine).
- **Target Frameworks & Technologies:** Kotlin 1.9+, gRPC Java (`grpc-netty-shaded`), Kotlin Coroutines, KUKSA Databroker v2/v1 protobuf definitions, Dynamic VSS YAML/JSON parser.
- **Testing Frameworks:** Kotest (`BehaviorSpec`), MockK, Kotlinx Coroutines Test, Gradle Test Runner.
- **Testing Strategy:** Strict Test-Driven Development (TDD) across Unit tests (`:mock-provider:test`), Integration contract tests, and CLI execution validation.

## Execution Sequence
1. Phase A: Dynamic VSS Parser Implementation & Unit Tests (`VssDefinitionParserTest`)
2. Phase B: VSS Actuator Discovery with Directory Scan & Fallback
3. Phase C: Generic Fallback Rule Resolution in `BehaviorRuleEngine`
4. Phase D: `MockProvider` v2 Stream Acknowledgment & `publishValue` Forwarding
5. Phase E: `MockProvider` v1 Target Reflector & `FIELD_VALUE` Reflection
6. Phase F: CLI Parameters & Informative Telemetry Formatter
7. Phase G: Full Suite Verification & Quality Gates

---

## Atomic Task Breakdown

### Task 1: Dynamic VSS File Discovery & Definition Parser (YAML & JSON)
- **Goal:** Build `VssDefinitionParser` and enhance `VssActuatorDiscovery` so that actuator signals are dynamically discovered from `.yaml` or `.json` files in `vss/` (e.g. `vss_rel_4.2.yaml`, `vss_rel_6.0.json`), supporting future VSS releases without recompiling.
- **Target Files:**
  - `mock-provider/src/main/kotlin/org/eclipse/kuksa/mockprovider/discovery/VssDefinitionParser.kt`
  - `mock-provider/src/main/kotlin/org/eclipse/kuksa/mockprovider/discovery/VssActuatorDiscovery.kt`
  - `mock-provider/src/test/kotlin/org/eclipse/kuksa/mockprovider/discovery/VssDefinitionParserTest.kt`
  - `mock-provider/src/test/kotlin/org/eclipse/kuksa/mockprovider/discovery/VssActuatorDiscoveryTest.kt`
- **Step-by-Step Execution:**
  1. Implement `VssDefinitionParser` with format detection (YAML via `kaml` / line-parser and JSON via `kotlinx.serialization.json`).
  2. For flat YAML format (`vss_rel_*.yaml`): extract all keys where `type == "actuator"`.
  3. For tree JSON format (`vss_rel_*.json`): recursively traverse tree and collect full dot-separated paths for all nodes with `"type": "actuator"`.
  4. In `VssActuatorDiscovery`, scan directory `vss/` (or CLI override `--vss-dir` / `--vss-file`) and load actuators from the latest or specified VSS definition files, falling back to compiled `VssVehicle` if no files are found.
  5. Write unit tests in `VssDefinitionParserTest.kt` asserting actuator extraction from both `vss_rel_4.2.yaml` and `vss_rel_4.2.json` (Scenario UT-2).
- **Validation Gate (TDD):**
  - Run `./gradlew :mock-provider:test --tests "org.eclipse.kuksa.mockprovider.discovery.*"`. 100% tests passing.

### Task 2: RuleLoader Fallback & Generic Rule Resolution
- **Goal:** Ensure `RuleLoader` handles missing/empty YAML files gracefully and `BehaviorRuleEngine` resolves unmapped actuators to a default direct-passthrough rule.
- **Target Files:**
  - `mock-provider/src/main/kotlin/org/eclipse/kuksa/mockprovider/engine/RuleLoader.kt`
  - `mock-provider/src/main/kotlin/org/eclipse/kuksa/mockprovider/engine/BehaviorRuleEngine.kt`
  - `mock-provider/src/test/kotlin/org/eclipse/kuksa/mockprovider/engine/BehaviorRuleEngineTest.kt`
- **Step-by-Step Execution:**
  1. Add tests in `BehaviorRuleEngineTest.kt` for Scenario UT-1 (resolving unmapped paths yields direct transform with target == actuator) and Scenario UT-4 (missing rule file returns empty list).
  2. Update `RuleLoader.load()` to catch missing file exceptions and return `emptyList()` with a user-friendly log.
  3. Ensure `BehaviorRuleEngine.resolveRule()` guarantees a non-null `BehaviorRule(actuator = path, sensor = path, delayMs = 0, transform = Transform.DIRECT)`.
- **Validation Gate (TDD):**
  - Run `./gradlew :mock-provider:test --tests "org.eclipse.kuksa.mockprovider.engine.BehaviorRuleEngineTest"`.

### Task 3: Stream Acknowledgment & BatchActuateStreamResponse Pipeline
- **Goal:** Correctly formulate and return `BatchActuateStreamResponse` messages for each incoming actuation request in `MockProvider`.
- **Target Files:**
  - `mock-provider/src/main/kotlin/org/eclipse/kuksa/mockprovider/provider/MockProvider.kt`
- **Step-by-Step Execution:**
  1. Inspect `val.proto` for `BatchActuateStreamResponse` structure (`signal_id` field and optional `error`).
  2. Implement response construction in `handleBatchActuate()` so each request in `batchActuate.actuateRequestsList` is acknowledged with its corresponding `signal_id`.
  3. Send the acknowledgment frame on `requestStream`.

### Task 4: Generic Auto-Forward & PublishValue Integration (V1 & V2)
- **Goal:** When an actuation request is processed, automatically forward/publish the actuated value to the Databroker via `publishValue(PublishValueRequestV2)` for v2 and via `update(FIELD_VALUE)` for v1 target reflection, logging confirmation.
- **Target Files:**
  - `mock-provider/src/main/kotlin/org/eclipse/kuksa/mockprovider/provider/MockProvider.kt`
- **Step-by-Step Execution:**
  1. For v2: In `applyRule()`, for `Transform.DIRECT`, directly call `publishSensorValue(targetSensor, directValue)` using `conn.kuksaValV2.publishValue()`.
  2. For v1: In `startV1TargetReflector()`, intercept `FIELD_ACTUATOR_TARGET` updates and execute `conn.kuksaValV1.update(FIELD_VALUE)`.
  3. Emit clear user feedback log: `"[v2 ACTUATE SUCCESS] Set $sensorPath -> $value"` and `"[v1 ACTUATE SUCCESS] Reflected $targetSensor -> $targetDatapoint"`.

### Task 5: Custom Rule Precedence & Delay/Ramp Support
- **Goal:** Verify that explicit YAML behavior rules (e.g. ramp transformations, sensor mappings, delays) continue to take precedence over generic passthrough.
- **Target Files:**
  - `mock-provider/src/main/kotlin/org/eclipse/kuksa/mockprovider/engine/BehaviorRuleEngine.kt`
  - `mock-provider/src/main/kotlin/org/eclipse/kuksa/mockprovider/provider/MockProvider.kt`
- **Step-by-Step Execution:**
  1. Verify matching rules in `BehaviorRuleEngine.findRule()` take precedence over fallback.
  2. Test ramp and toggle behaviors with mock datapoint fetch.

### Task 6: CLI Argument Parsing & Telemetry Logging
- **Goal:** Support `--generic`, `--host`, `--port`, `--rules`, `--vss-dir`, `--vss-file`, and `--verbose` CLI flags in `Main.kt`, and provide structured runtime status messages.
- **Target Files:**
  - `mock-provider/src/main/kotlin/org/eclipse/kuksa/mockprovider/Main.kt`
- **Step-by-Step Execution:**
  1. Add argument parsing in `Main.kt` for `--generic`, `--vss-dir`, `--vss-file`, and `--verbose`.
  2. Print banner and summary on startup.
