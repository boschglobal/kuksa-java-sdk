package org.eclipse.kuksa.testapp.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Direction {
    REQUEST,
    RESPONSE,
    EVENT,
    ERROR,
}

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val direction: Direction,
    val apiMethod: String,
    val payload: String,
) {
    fun format(): String {
        val timeStr = FORMATTER.format(Instant.ofEpochMilli(timestamp))
        return "[$timeStr] [${direction.name}] $apiMethod: $payload"
    }

    companion object {
        private val FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault())
    }
}
