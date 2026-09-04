# System Design: Java Test App & Mock Provider

## 1. Data Contracts and Domain Model

### Connection & Logging Models
```kotlin
data class ConnectionConfig(
    val host: String = "localhost",
    val port: Int = 55556,
    val jwt: String? = null,
    val useTls: Boolean = false,
    val apiVersion: ApiVersion = ApiVersion.V1,
)

enum class ApiVersion { V1, V2 }
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val direction: Direction,
    val apiMethod: String,
    val payload: String,
)

enum class Direction { REQUEST, RESPONSE, EVENT, ERROR }
```

### Signal Item and Serialization
```kotlin
data class SignalItem(
    val vssPath: String,
    val signalType: String,
    val dataType: String,
    val currentValue: String = "",
    val isSubscribed: Boolean = false,
)
```

`DataTypeSerializer`:
Converts string representations to `org.eclipse.kuksa.proto.v1.Types.Datapoint` and `org.eclipse.kuksa.proto.v2.Types.Value` for types `boolean`, `float`, `double`, `int32`, `uint32`, `int64`, `uint64`, `string`.

### Mock Provider Behavior Rules
```kotlin
@Serializable
data class BehaviorRule(
    val actuator: String,
    val sensor: String,
    @SerialName("delay_ms") val delayMs: Long = 0,
    val transform: Transform = Transform.DIRECT,
)

@Serializable
enum class Transform {
    @SerialName("direct") DIRECT,
    @SerialName("ramp") RAMP,
    @SerialName("toggle") TOGGLE
}

@Serializable
data class RuleConfig(val rules: List<BehaviorRule> = emptyList())
```

## 2. Error Taxonomy & Resilience
- gRPC exceptions are caught in `DataBrokerViewModel` coroutines and converted into `Direction.ERROR` `LogEntry` records without throwing unhandled exceptions.
- `DisconnectListener` detects broker shutdown and triggers UI state reset to `DISCONNECTED`.
