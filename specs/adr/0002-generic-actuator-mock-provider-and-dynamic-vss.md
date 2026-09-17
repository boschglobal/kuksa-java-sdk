# ADR-0002: Generic Actuator Mock Provider with Dynamic Multi-Version VSS Discovery and Dual V1/V2 Support

* **Status:** Accepted
* **Deciders:** SDK Architect, Developer
* **Date:** 2026-09-04

## Context and Problem Statement
When developers attempt to actuate VSS signals (e.g. `Vehicle.ADAS.CruiseControl.SpeedSet`) against KUKSA Databroker without an explicit behavior rule configured, the Databroker returns `UNAVAILABLE` ("No provider: run ./gradlew :mock-provider:run") or fails silently. Furthermore, actuators were previously discovered strictly via compile-time reflection of `VssVehicle`, preventing MockProvider from working when VSS definitions are updated or swapped in the `vss/` directory (e.g. `vss_rel_4.2.yaml`, `vss_rel_6.0.json`). Finally, developers require seamless actuation support across both `kuksa.val.v2` (bidirectional gRPC provider stream) and `kuksa.val.v1` (`FIELD_ACTUATOR_TARGET` reflection).

## Decision Drivers
* Generic zero-configuration actuation: Any actuator signal claimed by MockProvider must automatically accept actuations, publish/forward values to Databroker, and log confirmation.
* Dynamic VSS definition parsing: Dynamically scan and parse `.yaml` and `.json` definitions in `vss/` at startup to support arbitrary VSS releases without recompilation.
* Dual protocol support: Seamless parallel support for `kuksa.val.v2` (OpenProviderStream + ProvideActuationRequest + BatchActuateStreamResponse + publishValue) and `kuksa.val.v1` (Subscribe `FIELD_ACTUATOR_TARGET` on `Vehicle` + Update `FIELD_VALUE`).
* Preserved rule precedence: Explicit custom `BehaviorRule`s in YAML (e.g., ramp steps, toggles, custom target sensor redirection) retain execution priority over generic passthrough.

## Considered Options
* Option 1: Dynamic VSS file parser (YAML + JSON) + Generic fallback engine + Dual v1/v2 provider stream and reflector in `:mock-provider`.
* Option 2: Require manual entry of every single VSS signal in `behavior-rules.yaml`.
* Option 3: Hardcode compile-time reflection on `VssVehicle` and only support v2 actuation.

## Decision Outcome
Chosen Option: Option 1, because it offers a zero-maintenance developer experience, guarantees 100% actuator signal coverage across any VSS model version, and seamlessly bridges both v1 and v2 API workflows.

### Positive Consequences
* Zero-configuration startup: Works immediately for any signal without editing YAML files.
* Decoupled from build-time VSS version: Swapping `vss/` files instantly updates claimed actuators.
* Complete API coverage: Both v1 and v2 client applications are fully supported simultaneously.
* High observability: Clear console telemetry for every actuate, ack, and value reflection event.

### Negative Consequences
* Initial startup takes ~20-50ms to parse the VSS file and register all signals with Databroker.
