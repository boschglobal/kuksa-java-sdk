# Architecture Specification: Generic Actuator Mock Provider

## 1. System Context & Container Topology

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                                   Host Machine                                   │
│                                                                                  │
│   ┌────────────────────────────────────────────────────────┐                     │
│   │               KUKSA Databroker (:55556)                │                     │
│   │                                                        │                     │
│   │   v1 Service:               v2 Service:                │                     │
│   │   - Update (TARGET/VALUE)   - OpenProviderStream       │                     │
│   │   - Subscribe (Flow)        - Actuate / BatchActuate   │                     │
│   │                             - PublishValue             │                     │
│   └───────────────▲────────────────────────▲───────────────┘                     │
│                   │                        │                                     │
│      (v1 Update / Subscribe)   (v2 ProviderStream / Publish)                     │
│                   │                        │                                     │
│   ┌───────────────┴────────────────────────┴───────────────┐                     │
│   │             Generic MockProvider Process               │                     │
│   │                                                        │                     │
│   │   ┌─────────────────────┐   ┌──────────────────────┐   │                     │
│   │   │ VssDefinitionParser │   │  BehaviorRuleEngine  │   │                     │
│   │   │ (YAML / JSON)       │   │  - Rules from YAML   │   │                     │
│   │   └──────────▲──────────┘   │  - Generic Fallback  │   │                     │
│   │              │              └──────────────────────┘   │                     │
│   │     ┌────────┴────────┐                                │                     │
│   │     │  vss/ Directory │      ┌──────────────────────┐  │                     │
│   │     └─────────────────┘      │  DashboardState      │  │                     │
│   │                              │  (shared state + SSE) │  │                     │
│   │                              └──────────┬───────────┘  │                     │
│   │                                         │              │                     │
│   │                              ┌──────────┴───────────┐  │                     │
│   │                              │ MockProviderWebServer │  │                     │
│   │                              │  HTTP :8080 (opt-in) │  │                     │
│   │                              └──────────────────────┘  │                     │
│   └────────────────────────────────────────────────────────┘                     │
│                                        │                                        │
│                              Browser (Dashboard UI)                              │
│                              GET / → index.html                                  │
│                              GET /api/state  (poll 3s)                           │
│                              GET /api/logs/stream  (SSE)                         │
│                              GET /api/actuators?filter=                          │
└──────────────────────────────────────────────────────────────────────────────────┘
```

## 2. Component Structure
- `VssDefinitionParser`: Dynamic parser reading `.yaml` / `.json` files in `vss/` or custom path.
- `VssActuatorDiscovery`: Orchestrator querying `VssDefinitionParser`, Databroker `ListMetadata`, and fallback `VssVehicle`.
- `BehaviorRuleEngine`: Evaluator that matches explicit rules or creates generic auto-forward passthrough rules.
- `MockProvider`: Daemon managing the gRPC channel, the v2 bidirectional stream (`openProviderStream`), and the v1 target reflector coroutine.
- `DashboardState`: Thread-safe shared state holding the log ring buffer, SSE subscriber list, actuator registry, and runtime counters. Populated by `MockProvider`; consumed by `MockProviderWebServer`.
- `MockProviderWebServer`: Embedded Java `HttpServer` (no external dependencies) serving the live dashboard HTML and REST/SSE APIs. Started only when `--web-port <N>` is provided.

## 3. Deployment
- **Local (headless):** `./gradlew :mock-provider:run --args="--host localhost --port 55556"`
- **Local (with dashboard):** `./gradlew :mock-provider:run --args="--host localhost --port 55556 --web-port 8080"`
- **Docker Compose:** `cd mock-environment && docker-compose up` — builds both `databroker` and `mock-provider` containers; dashboard at `http://localhost:8080`.
