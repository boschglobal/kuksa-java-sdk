package org.eclipse.kuksa.mockprovider.engine

import org.eclipse.kuksa.mockprovider.model.BehaviorRule
import org.eclipse.kuksa.mockprovider.model.Transform
import org.eclipse.kuksa.proto.v2.Types

class BehaviorRuleEngine(val rules: List<BehaviorRule> = emptyList()) {
    val actuatorPaths: List<String>
        get() = rules.map { it.actuator }

    fun findRule(actuatorPath: String): BehaviorRule? {
        return rules.firstOrNull { matchesPattern(it.actuator, actuatorPath) }
    }

    fun resolveRule(actuatorPath: String): BehaviorRule {
        return findRule(actuatorPath)
            ?: BehaviorRule(actuator = actuatorPath, sensor = actuatorPath, delayMs = 0, transform = Transform.DIRECT)
    }

    fun resolveTargetSensor(rule: BehaviorRule, actualActuatorPath: String): String {
        val sensor = rule.sensor
        return if (sensor.isNullOrBlank()) actualActuatorPath else sensor
    }

    fun matchesPattern(pattern: String, path: String): Boolean {
        if (pattern == path) return true
        val regexPattern = buildString {
            append("^")
            var i = 0
            while (i < pattern.length) {
                when {
                    pattern.startsWith("**", i) -> {
                        append(".*")
                        i += 2
                    }
                    pattern.startsWith("*", i) -> {
                        append("[^.]+")
                        i += 1
                    }
                    pattern[i] == '.' -> {
                        append("\\.")
                        i += 1
                    }
                    else -> {
                        append(Regex.escape(pattern[i].toString()))
                        i += 1
                    }
                }
            }
            append("$")
        }
        return Regex(regexPattern).matches(path)
    }

    fun computeDirectValue(
        @Suppress("UNUSED_PARAMETER") rule: BehaviorRule,
        target: Types.Value,
    ): Types.Value {
        return target
    }

    fun computeRampSteps(
        @Suppress("UNUSED_PARAMETER") rule: BehaviorRule,
        current: Float,
        target: Float,
        steps: Int = 10,
    ): List<Float> {
        if (steps <= 0) return listOf(target)
        val stepSize = (target - current) / steps
        return (1..steps).map { i ->
            if (i == steps) target else current + stepSize * i
        }
    }

    fun computeToggle(
        @Suppress("UNUSED_PARAMETER") rule: BehaviorRule,
        current: Types.Value,
    ): Types.Value {
        val currentValue = current.bool
        return Types.Value.newBuilder().setBool(!currentValue).build()
    }
}
