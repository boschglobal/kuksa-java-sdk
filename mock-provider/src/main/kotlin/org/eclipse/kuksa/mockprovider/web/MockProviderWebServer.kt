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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.Executors

@Serializable
private data class DashboardStateResponse(
    val claimedSignals: Int,
    val actuationsProcessed: Long,
    val customRulesCount: Int,
    val mode: String,
    val host: String,
    val port: Int,
    val streamActive: Boolean,
)

@Serializable
private data class ActuatorsResponse(
    val total: Int,
    val filtered: Int,
    val items: List<ActuatorEntry>,
)

@Serializable
private data class StopResponse(
    val status: String,
)

@Suppress("TooManyFunctions")
class MockProviderWebServer(
    private val state: DashboardState,
    private val webPort: Int,
    private val onStop: () -> Unit = {},
) {
    private var server: HttpServer? = null

    fun start() {
        val s = HttpServer.create(InetSocketAddress(webPort), 0)
        server = s
        s.executor = Executors.newCachedThreadPool()
        registerEndpoints(s)
        s.start()

        println("==================================================")
        println("  Web Dashboard ready:")
        println("  http://localhost:$webPort")
        println("==================================================")
    }

    private fun registerEndpoints(s: HttpServer) {
        // Register more-specific paths before the catch-all "/" context (prefix-match order matters)
        s.createContext("/api/logs/stream") { exchange ->
            if (exchange.requestMethod == "GET") {
                handleSse(exchange)
            } else {
                exchange.sendResponseHeaders(HTTP_METHOD_NOT_ALLOWED, -1)
                exchange.close()
            }
        }
        s.createContext("/api/actuators") { exchange ->
            if (exchange.requestMethod == "GET") {
                sendJson(exchange, buildActuatorsJson(queryParam(exchange, "filter")))
            } else {
                exchange.sendResponseHeaders(HTTP_METHOD_NOT_ALLOWED, -1)
                exchange.close()
            }
        }
        s.createContext("/api/state") { exchange ->
            if (exchange.requestMethod == "GET") {
                sendJson(exchange, buildStateJson())
            } else {
                exchange.sendResponseHeaders(HTTP_METHOD_NOT_ALLOWED, -1)
                exchange.close()
            }
        }
        s.createContext("/api/control/stop") { exchange ->
            if (exchange.requestMethod == "POST") {
                sendJson(exchange, Json.encodeToString(StopResponse("stopping")))
                Thread {
                    Thread.sleep(STOP_DELAY_MS)
                    onStop()
                }.start()
            } else {
                exchange.sendResponseHeaders(HTTP_METHOD_NOT_ALLOWED, -1)
                exchange.close()
            }
        }
        s.createContext("/") { exchange ->
            if (exchange.requestMethod == "GET") {
                val path = exchange.requestURI.path
                if (path == "/" || path == "/index.html") {
                    serveResource(exchange, "/web/index.html", "text/html; charset=utf-8")
                } else {
                    exchange.sendResponseHeaders(HTTP_NOT_FOUND, -1)
                    exchange.close()
                }
            } else {
                exchange.sendResponseHeaders(HTTP_METHOD_NOT_ALLOWED, -1)
                exchange.close()
            }
        }
    }

    fun stop() { server?.stop(0) }

    @Suppress("TooGenericExceptionCaught")
    private fun handleSse(exchange: HttpExchange) {
        with(exchange.responseHeaders) {
            add("Content-Type", "text/event-stream")
            add("Cache-Control", "no-cache")
            add("Connection", "keep-alive")
            add("Access-Control-Allow-Origin", "*")
        }
        exchange.sendResponseHeaders(HTTP_OK, CHUNKED_RESPONSE)
        val out = exchange.responseBody
        state.addSseClient(out)

        // Replay buffered logs to newly connected client
        state.getLogs().forEach { entry ->
            try {
                out.write("data: ${entry.toJson()}\n\n".toByteArray(Charsets.UTF_8))
                out.flush()
            } catch (_: Exception) {
                // Client disconnected during log replay; nothing useful to log from a broken-pipe exception
                state.removeSseClient(out)
                runCatching { exchange.close() }
                return
            }
        }

        // Keep connection alive with periodic heartbeat comments
        try {
            while (true) {
                Thread.sleep(HEARTBEAT_INTERVAL_MS)
                out.write(": heartbeat\n\n".toByteArray(Charsets.UTF_8))
                out.flush()
            }
        } catch (_: Exception) {
            // Client disconnected or server stopped
        } finally {
            state.removeSseClient(out)
            runCatching { exchange.close() }
        }
    }

    private fun buildStateJson(): String {
        val response = DashboardStateResponse(
            claimedSignals = state.claimedSignals.get(),
            actuationsProcessed = state.actuationsProcessed.get(),
            customRulesCount = state.customRulesCount,
            mode = "Generic Auto-Forward",
            host = state.host,
            port = state.port,
            streamActive = state.streamActive,
        )
        return Json.encodeToString(response)
    }

    private fun buildActuatorsJson(filter: String?): String {
        val list = if (filter.isNullOrBlank()) {
            state.actuators
        } else {
            state.actuators.filter { it.path.contains(filter, ignoreCase = true) }
        }
        val response = ActuatorsResponse(
            total = state.claimedSignals.get(),
            filtered = list.size,
            items = list,
        )
        return Json.encodeToString(response)
    }

    private fun serveResource(exchange: HttpExchange, resourcePath: String, contentType: String) {
        val bytes = javaClass.getResourceAsStream(resourcePath)?.readBytes()
            ?: run {
                exchange.sendResponseHeaders(HTTP_NOT_FOUND, -1)
                exchange.close()
                return
            }
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(HTTP_OK, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendJson(exchange: HttpExchange, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(HTTP_OK, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun queryParam(exchange: HttpExchange, name: String): String? {
        val query = exchange.requestURI.query ?: return null
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == name }
            ?.getOrNull(1)
            ?.let { URLDecoder.decode(it, "UTF-8") }
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val STOP_DELAY_MS = 200L
        private const val HTTP_OK = 200
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_METHOD_NOT_ALLOWED = 405
        private const val CHUNKED_RESPONSE = 0L
    }
}
