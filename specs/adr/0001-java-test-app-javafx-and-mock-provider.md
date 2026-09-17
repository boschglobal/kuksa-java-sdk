# ADR-0001: JavaFX Desktop Test App and Mock Provider Submodules

* **Status:** Accepted
* **Deciders:** SDK Architect, Developer
* **Date:** 2026-09-03

## Context and Problem Statement
The `kuksa-java-sdk` provides client protocols for KUKSA Databroker v1 and v2. Currently, manual and iterative verification is limited to non-interactive CLI samples or unit tests. An interactive desktop application is required to test and demonstrate all public APIs of both `KuksaValV1Protocol` and `KuksaValV2Protocol` with generated VSS models and raw path inputs, along with a standalone Mock Provider to handle actuator requests and simulate vehicle response behaviors.

## Decision Drivers
* Need for interactive desktop UI on JVM (macOS/Linux/Windows) without Android emulator dependency.
* Full API coverage of v1 (`KuksaValV1Protocol`) and v2 (`KuksaValV2Protocol`).
* Support for compile-time generated VSS signals (via `org.eclipse.velocitas.vss-processor-plugin`) and raw free-form path inputs.
* Clean separation of concerns: pure Kotlin `DataBrokerViewModel` with `StateFlow` and zero JavaFX coupling, observed by JavaFX controllers via `kotlinx-coroutines-javafx`.
* In-process and standalone testability of actuator feedback loop via a dedicated `:mock-provider` module.

## Considered Options
* Option 1: JavaFX 21 desktop application (`:java-test-app`) with separate `:mock-provider` module.
* Option 2: Android test app (`:kuksa-android-sdk-test-app`).
* Option 3: Terminal CLI REPL/TUI application.

## Decision Outcome
Chosen Option: Option 1 (JavaFX 21 + `:mock-provider`), because it provides a rich multi-tab GUI, runs natively on any desktop JVM without Android SDK/device overhead, supports responsive asynchronous Flow observation via coroutines, and enables full API demonstration.

### Positive Consequences
* Complete coverage of all 8 v1 methods and 10 v2 methods.
* Fast local test cycle using standard JVM Gradle build.
* Clean architecture allowing unit testing of ViewModel, serializer, and MockProvider behavior rules with zero UI dependencies.
* UI testable via TestFX.

### Negative Consequences
* Requires JavaFX runtime / plugin configured in Gradle.
* Requires host Netty shaded gRPC transport rather than OkHttp.
