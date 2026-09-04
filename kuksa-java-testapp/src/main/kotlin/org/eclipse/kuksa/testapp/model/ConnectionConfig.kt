package org.eclipse.kuksa.testapp.model

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class ConnectionConfig(
    val host: String = "localhost",
    val port: Int = 55556,
    val jwt: String? = null,
    val useTls: Boolean = false,
)
