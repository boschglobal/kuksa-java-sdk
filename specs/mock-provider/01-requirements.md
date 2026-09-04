# Requirements Specification: Generic Actuator Mock Provider

## 1. Executive Summary & Goals
- **Feature ID:** FR-MOCK-001
- **Target:** Sub-module `:mock-provider`
- **Objective:** Provide a generic, dynamic Mock Provider for KUKSA Databroker that dynamically claims all actuator signals from the `vss/` folder, auto-forwards and publishes actuated values back to the Databroker for both `kuksa.val.v2` and `kuksa.val.v1`, and logs successful signal modifications.

## 2. Actors & Use Cases
- **Actor 1: Test App / SDK Client:** Sends `actuate()` / `batchActuate()` (v2) or `update(FIELD_ACTUATOR_TARGET)` (v1) to Databroker.
- **Actor 2: KUKSA Databroker:** Routes actuation events to registered providers and stores vehicle signal datapoints.
- **Actor 3: MockProvider Daemon:** Receives actuation requests, evaluates rules (or generic fallback), acknowledges the stream, sets the datapoint, and logs status.

## 3. Functional Requirements (MoSCoW)
- **FR-MOCK-001 (Must):** Dynamic VSS discovery from `vss/` directory supporting both flat YAML and tree JSON definitions across releases (e.g. 4.2, 6.0).
- **FR-MOCK-002 (Must):** Auto-claim all discovered actuators via `ProvideActuationRequest` on `kuksa.val.v2` `openProviderStream`.
- **FR-MOCK-003 (Must):** Acknowledge incoming `BatchActuateStreamRequest`s using `BatchActuateStreamResponse(signalId)`.
- **FR-MOCK-004 (Must):** Generic auto-forward passthrough: Publish actuated value to target signal via `publishValue()` (v2) or `update(FIELD_VALUE)` (v1) without requiring YAML rule entries.
- **FR-MOCK-005 (Must):** Parallel `kuksa.val.v1` target reflector for `FIELD_ACTUATOR_TARGET` on `Vehicle` subtree.
- **FR-MOCK-006 (Should):** Preserve custom YAML `BehaviorRule` precedence (delay, ramp, toggle, custom sensor mapping).
- **FR-MOCK-007 (Should):** Informative telemetry logging upon actuation reception, acknowledgment, and successful value publication.
- **FR-MOCK-008 (Could):** Dynamic metadata discovery via Databroker `ListMetadata(root = "Vehicle")`.
