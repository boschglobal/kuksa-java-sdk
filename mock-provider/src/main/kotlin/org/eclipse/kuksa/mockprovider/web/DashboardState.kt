/*
 * Copyright (c) 2023 - 2026 Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.kuksa.mockprovider.web

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.kuksa.mockprovider.engine.BehaviorRuleEngine
import org.eclipse.kuksa.mockprovider.model.Transform
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Serializable
data class LogEntry(
    val timestamp: String,
    val level: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
data class ActuatorEntry(
    val path: String,
    val strategy: String,
    val targetPath: String,
    val isCustomRule: Boolean,
)

fun LogEntry.toJson(): String = Json.encodeToString(this)

class DashboardState(val host: String = "localhost", val port: Int = 55556) {
    val claimedSignals = AtomicInteger(0)
    val actuationsProcessed = AtomicLong(0)
    var customRulesCount: Int = 0
    var streamActive: Boolean = false

    private val logLock = Any()
    private val logBuffer = ArrayDeque<LogEntry>()
    private val actuatorList = CopyOnWriteArrayList<ActuatorEntry>()
    private val sseClients = CopyOnWriteArrayList<OutputStream>()

    fun getLogs(): List<LogEntry> = synchronized(logLock) { logBuffer.toList() }
    val actuators: List<ActuatorEntry> get() = actuatorList.toList()

    fun log(level: String, message: String, details: Map<String, String> = emptyMap()) {
        val ts = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val entry = LogEntry(ts, level, message, details)
        synchronized(logLock) {
            logBuffer.addLast(entry)
            if (logBuffer.size > MAX_LOGS) logBuffer.removeFirst()
        }
        broadcastSse(entry)
    }

    fun registerActuators(paths: List<String>, engine: BehaviorRuleEngine) {
        actuatorList.clear()
        paths.forEach { path ->
            val rule = engine.findRule(path)
            val strategy = when {
                rule == null -> "Auto-Forward (Direct)"
                rule.transform == Transform.TOGGLE -> "Rule: Toggle (${rule.delayMs}ms)"
                rule.transform == Transform.RAMP -> "Rule: Ramp (${rule.delayMs}ms)"
                else -> "Rule: Direct (${rule.delayMs}ms delay)"
            }
            actuatorList.add(ActuatorEntry(path, strategy, rule?.sensor ?: path, rule != null))
        }
        claimedSignals.set(paths.size)
    }

    fun addSseClient(out: OutputStream) { sseClients.add(out) }
    fun removeSseClient(out: OutputStream) { sseClients.remove(out) }

    private fun broadcastSse(entry: LogEntry) {
        val bytes = "data: ${entry.toJson()}\n\n".toByteArray(Charsets.UTF_8)
        val dead = mutableListOf<OutputStream>()
        sseClients.forEach { out ->
            try {
                out.write(bytes)
                out.flush()
            } catch (_: Exception) {
                dead.add(out)
            }
        }
        dead.forEach { sseClients.remove(it) }
    }

    companion object {
        private const val MAX_LOGS = 500
    }
}
