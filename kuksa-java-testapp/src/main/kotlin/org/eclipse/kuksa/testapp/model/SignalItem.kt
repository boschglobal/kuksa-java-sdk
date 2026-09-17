package org.eclipse.kuksa.testapp.model

import org.eclipse.kuksa.vsscore.model.VssNode
import org.eclipse.kuksa.vsscore.model.VssSignal

data class SignalItem(
    val vssPath: String,
    val signalType: String,
    val dataType: String,
    val currentValue: String = "",
    val isSubscribed: Boolean = false,
    val vssNode: VssNode? = null,
) {
    companion object {
        fun fromVssNode(node: VssNode): SignalItem {
            val path = node.vssPath
            val type = node.type
            val dataType = if (node is VssSignal<*>) {
                node.dataType.simpleName ?: "Unknown"
            } else {
                "Node"
            }
            val currentValue = if (node is VssSignal<*>) {
                node.value.toString()
            } else {
                ""
            }
            return SignalItem(
                vssPath = path,
                signalType = type,
                dataType = dataType,
                currentValue = currentValue,
                isSubscribed = false,
                vssNode = node,
            )
        }
    }
}
