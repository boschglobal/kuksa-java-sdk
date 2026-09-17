package org.eclipse.kuksa.mockprovider.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Transform {
    @SerialName("direct")
    DIRECT,

    @SerialName("ramp")
    RAMP,

    @SerialName("toggle")
    TOGGLE,
}

@Serializable
data class BehaviorRule(
    val actuator: String,
    val sensor: String? = null,
    @SerialName("delay_ms") val delayMs: Long = 0,
    val transform: Transform = Transform.DIRECT,
)

@Serializable
data class RuleConfig(
    val rules: List<BehaviorRule> = emptyList(),
)
