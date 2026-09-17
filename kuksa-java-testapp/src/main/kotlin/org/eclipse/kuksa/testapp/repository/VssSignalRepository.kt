package org.eclipse.kuksa.testapp.repository

import org.eclipse.kuksa.testapp.model.SignalItem
import org.eclipse.kuksa.vsscore.model.VssNode
import org.eclipse.velocitas.vss.VssVehicle

object VssSignalRepository {
    private val cachedSignals: List<SignalItem> by lazy {
        val root = VssVehicle()
        flattenNode(root).map { SignalItem.fromVssNode(it) }
    }

    fun allSignals(): List<SignalItem> = cachedSignals

    fun sensors(): List<SignalItem> = cachedSignals.filter {
        it.signalType.equals("sensor", ignoreCase = true)
    }

    fun actuators(): List<SignalItem> = cachedSignals.filter {
        it.signalType.equals("actuator", ignoreCase = true)
    }

    private fun flattenNode(node: VssNode): List<VssNode> {
        return listOf(node) + node.children.flatMap { flattenNode(it) }
    }
}
