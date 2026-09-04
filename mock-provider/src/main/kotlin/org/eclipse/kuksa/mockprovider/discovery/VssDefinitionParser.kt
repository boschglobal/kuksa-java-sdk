package org.eclipse.kuksa.mockprovider.discovery

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

object VssDefinitionParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseActuatorsFromDirectory(directory: File): List<String> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()
        val files = directory.listFiles { file ->
            file.isFile && (
                file.name.endsWith(".yaml", ignoreCase = true) ||
                    file.name.endsWith(".yml", ignoreCase = true) ||
                    file.name.endsWith(".json", ignoreCase = true)
                )
        } ?: return emptyList()

        return files.flatMap { parseActuatorsFromFile(it) }.distinct()
    }

    fun parseActuatorsFromFile(file: File): List<String> {
        if (!file.exists() || !file.isFile) return emptyList()
        val content = runCatching { file.readText() }.getOrNull() ?: return emptyList()

        return when {
            file.name.endsWith(".json", ignoreCase = true) -> parseJsonActuators(content)
            file.name.endsWith(".yaml", ignoreCase = true) ||
                file.name.endsWith(".yml", ignoreCase = true) -> parseYamlActuators(content)
            else -> emptyList()
        }
    }

    fun parseYamlActuators(yamlContent: String): List<String> {
        val actuators = mutableListOf<String>()
        var currentPath: String? = null
        var isActuator = false

        for (line in yamlContent.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            if (isTopLevelKey(line, trimmed)) {
                if (currentPath != null && isActuator) {
                    actuators.add(currentPath)
                }
                currentPath = extractKeyPath(trimmed)
                isActuator = false
            } else if (currentPath != null && isActuatorDeclaration(trimmed)) {
                isActuator = true
            }
        }
        if (currentPath != null && isActuator) {
            actuators.add(currentPath)
        }
        return actuators
    }

    private fun isTopLevelKey(line: String, trimmed: String): Boolean {
        return !line.startsWith(" ") && !line.startsWith("\t") && trimmed.endsWith(":")
    }

    private fun extractKeyPath(trimmed: String): String {
        return trimmed.removeSuffix(":").trim().removeSurrounding("\"").removeSurrounding("'")
    }

    private fun isActuatorDeclaration(trimmed: String): Boolean {
        if (!trimmed.startsWith("type:")) return false
        val typeVal = trimmed.removePrefix("type:").trim().removeSurrounding("\"").removeSurrounding("'")
        return typeVal.equals("actuator", ignoreCase = true)
    }

    fun parseJsonActuators(jsonContent: String): List<String> {
        val rootElement = runCatching { json.parseToJsonElement(jsonContent) }.getOrNull() ?: return emptyList()
        val actuators = mutableListOf<String>()

        if (rootElement is JsonObject) {
            for ((key, value) in rootElement) {
                traverseJsonNode(key, value, actuators)
            }
        }
        return actuators
    }

    private fun traverseJsonNode(path: String, element: JsonElement, result: MutableList<String>) {
        if (element !is JsonObject) return

        val type = element["type"]?.jsonPrimitive?.content
        if (type.equals("actuator", ignoreCase = true)) {
            result.add(path)
        }

        val children = element["children"]
        if (children is JsonObject) {
            for ((childKey, childValue) in children) {
                val childPath = if (path.isEmpty()) childKey else "$path.$childKey"
                traverseJsonNode(childPath, childValue, result)
            }
        }
    }
}
