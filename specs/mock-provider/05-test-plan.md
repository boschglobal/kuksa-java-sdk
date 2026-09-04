# Test Plan: Generic Actuator Mock Provider

## 1. Unit Tests (`:mock-provider:test`)
- `VssDefinitionParserTest`:
  - UT-1: Parse actuator signals from YAML format (`vss_rel_4.2.yaml`).
  - UT-2: Parse actuator signals from JSON hierarchy (`vss_rel_4.2.json`).
  - UT-3: Handle missing, malformed, or empty definition files gracefully.
- `VssActuatorDiscoveryTest`:
  - UT-4: Discovers all actuators from `vss/` folder dynamically.
  - UT-5: Falls back gracefully if `vss/` directory is missing.
- `BehaviorRuleEngineTest`:
  - UT-6: Unmatched actuator returns generic auto-forward passthrough rule (`Transform.DIRECT`, `delayMs = 0`, `target == actuator`).
  - UT-7: Explicit YAML rules take precedence over fallback.
- `MockProviderTest`:
  - UT-8: Builds valid `BatchActuateStreamResponse` with `signalId` populated.

## 2. Integration Tests
- IT-1: v2 `actuate` on generic unconfigured signal updates Databroker state.
- IT-2: v1 `update(FIELD_ACTUATOR_TARGET)` reflects to `FIELD_VALUE`.
