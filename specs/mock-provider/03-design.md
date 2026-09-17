# Design Specification: Generic Actuator Mock Provider

## 1. Class Design & Data Models

### 1.1 VssDefinitionParser
```kotlin
object VssDefinitionParser {
    fun parseActuatorsFromDirectory(directory: File): List<String>
    fun parseActuatorsFromFile(file: File): List<String>
    fun parseYamlActuators(yamlContent: String): List<String>
    fun parseJsonActuators(jsonContent: String): List<String>
}
```

### 1.2 BehaviorRuleEngine Enhancements
```kotlin
fun resolveRule(actuatorPath: String): BehaviorRule =
    findRule(actuatorPath) ?: BehaviorRule(
        actuator = actuatorPath,
        sensor = actuatorPath,
        delayMs = 0,
        transform = Transform.DIRECT,
    )
```

### 1.2 Web Dashboard Classes

```kotlin
// Shared state; passed to MockProvider and MockProviderWebServer
class DashboardState(val host: String, val port: Int) {
    val claimedSignals: AtomicInteger
    val actuationsProcessed: AtomicLong
    var customRulesCount: Int
    var streamActive: Boolean
    fun log(level: String, message: String, details: Map<String, String> = emptyMap())
    fun registerActuators(paths: List<String>, engine: BehaviorRuleEngine)
    fun addSseClient(out: OutputStream)
    fun removeSseClient(out: OutputStream)
}

// Embedded HTTP server; started only when --web-port is provided
class MockProviderWebServer(
    private val state: DashboardState,
    private val webPort: Int,
    private val onStop: () -> Unit = {},
) {
    fun start()   // registers routes and starts HttpServer
    fun stop()
}
```

**SSE Protocol:** `GET /api/logs/stream` keeps the response open; each log event is sent as `data: <json>\n\n`. New clients receive a replay of the last 500 buffered log entries, then live events. A heartbeat comment (`: heartbeat\n\n`) is sent every 15 s to keep idle connections alive.

### 1.3 MockProvider v2 & v1 Pipeline
```kotlin
// v2 Acknowledgment & Forwarding
private fun handleBatchActuate(batchActuate: KuksaValV2.BatchActuateStreamRequest) {
    for (req in batchActuate.actuateRequestsList) {
        val signalId = req.signalId
        val ack = KuksaValV2.OpenProviderStreamRequest.newBuilder()
            .setBatchActuateStreamResponse(
                KuksaValV2.BatchActuateStreamResponse.newBuilder()
                    .setSignalId(signalId)
                    .build()
            )
            .build()
        requestStream?.onNext(ack)

        val actuatorPath = signalId.path
        val rule = engine.resolveRule(actuatorPath)
        val targetSensor = engine.resolveTargetSensor(rule, actuatorPath)
        scope.launch {
            applyRule(rule, targetSensor, req.value)
        }
    }
}
```
