package org.eclipse.kuksa.mockprovider.engine

import com.charleskorn.kaml.Yaml
import org.eclipse.kuksa.mockprovider.model.BehaviorRule
import org.eclipse.kuksa.mockprovider.model.RuleConfig
import java.io.File

object RuleLoader {
    fun load(path: String): List<BehaviorRule> {
        val file = when {
            File(path).exists() -> File(path)
            File("..", path).exists() -> File("..", path)
            else -> File(path)
        }
        if (!file.exists() || !file.isFile) {
            return emptyList()
        }
        return runCatching {
            val yamlString = file.readText()
            val config = Yaml.default.decodeFromString(RuleConfig.serializer(), yamlString)
            config.rules
        }.onFailure {
            println("Warning: Could not parse rules from $path: ${it.message}. Defaulting to generic auto-forward.")
        }.getOrDefault(emptyList())
    }
}
