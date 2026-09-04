package org.eclipse.kuksa.mockprovider.discovery

import org.eclipse.kuksa.vsscore.model.VssNode
import org.eclipse.kuksa.vsscore.model.VssSignal
import java.io.File

object VssActuatorDiscovery {
    fun discoverActuatorPaths(
        vssDirectory: File = File("vss"),
        vssFile: File? = null,
        fallbackRoot: VssNode? = null,
    ): List<String> {
        val discovered = mutableListOf<String>()

        val effectiveDir = when {
            vssDirectory.exists() -> vssDirectory
            File("..", vssDirectory.path).exists() -> File("..", vssDirectory.path)
            else -> vssDirectory
        }

        val effectiveFile = when {
            vssFile != null && vssFile.exists() -> vssFile
            vssFile != null && File("..", vssFile.path).exists() -> File("..", vssFile.path)
            else -> vssFile
        }

        if (effectiveFile != null && effectiveFile.exists()) {
            discovered.addAll(VssDefinitionParser.parseActuatorsFromFile(effectiveFile))
        } else if (effectiveDir.exists() && effectiveDir.isDirectory) {
            discovered.addAll(VssDefinitionParser.parseActuatorsFromDirectory(effectiveDir))
        }

        if (discovered.isEmpty() && fallbackRoot != null) {
            discovered.addAll(discoverFromNode(fallbackRoot))
        }

        return discovered.distinct()
    }

    fun discoverFromNode(root: VssNode): List<String> {
        return flattenNode(root)
            .filter { it is VssSignal<*> && it.type.equals("actuator", ignoreCase = true) }
            .map { it.vssPath }
    }

    private fun flattenNode(node: VssNode): List<VssNode> {
        return listOf(node) + node.children.flatMap { flattenNode(it) }
    }
}
