package org.eclipse.kuksa.mockprovider.engine

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.eclipse.kuksa.mockprovider.model.BehaviorRule
import org.eclipse.kuksa.mockprovider.model.Transform
import org.eclipse.kuksa.proto.v2.Types

class BehaviorRuleEngineTest : BehaviorSpec({
    Given("A BehaviorRuleEngine with configured rules") {
        val rules = listOf(
            BehaviorRule(
                actuator = "Vehicle.ADAS.CruiseControl.SpeedSet",
                sensor = "Vehicle.Speed",
                delayMs = 500,
                transform = Transform.RAMP,
            ),
            BehaviorRule(
                actuator = "Vehicle.ADAS.ABS.IsEnabled",
                sensor = "Vehicle.ADAS.ABS.IsEngaged",
                delayMs = 0,
                transform = Transform.DIRECT,
            ),
            BehaviorRule(
                actuator = "Vehicle.ADAS.CruiseControl.IsActive",
                sensor = "Vehicle.ADAS.CruiseControl.IsActive",
                delayMs = 0,
                transform = Transform.TOGGLE,
            ),
        )
        val engine = BehaviorRuleEngine(rules)

        When("Finding rule by actuator path") {
            Then("It returns the matched rule or null") {
                val rule = engine.findRule("Vehicle.ADAS.CruiseControl.SpeedSet")
                rule?.sensor shouldBe "Vehicle.Speed"
                rule?.transform shouldBe Transform.RAMP
                engine.findRule("Vehicle.Unknown") shouldBe null
            }
        }

        When("Computing direct value (UT-5)") {
            val rule = engine.findRule("Vehicle.ADAS.ABS.IsEnabled")!!
            val targetValue = Types.Value.newBuilder().setBool(true).build()
            val result = engine.computeDirectValue(rule, targetValue)

            Then("Result equals target value") {
                result.bool shouldBe true
            }
        }

        When("Computing ramp steps (UT-6)") {
            val rule = engine.findRule("Vehicle.ADAS.CruiseControl.SpeedSet")!!
            val steps = engine.computeRampSteps(rule, current = 20.0f, target = 120.0f, steps = 10)

            Then("Result contains 10 steps ending at target") {
                steps shouldHaveSize 10
                steps.last() shouldBe 120.0f
                steps.first() shouldBe 30.0f
            }
        }

        When("Computing toggle value (UT-7)") {
            val rule = engine.findRule("Vehicle.ADAS.CruiseControl.IsActive")!!
            val currentValue = Types.Value.newBuilder().setBool(false).build()
            val toggled = engine.computeToggle(rule, currentValue)

            Then("Result boolean is inverted") {
                toggled.bool shouldBe true
            }
        }

        When("Matching wildcard patterns with * and **") {
            val wildcardRules = listOf(
                BehaviorRule(
                    actuator = "Vehicle.Cabin.Door.**.Window.Position",
                    delayMs = 200,
                    transform = Transform.DIRECT,
                ),
                BehaviorRule(
                    actuator = "Vehicle.Body.Lights.Beam.*.IsOn",
                    sensor = "Vehicle.Body.Lights.Beam.High.IsActive",
                    delayMs = 0,
                    transform = Transform.DIRECT,
                ),
            )
            val wildcardEngine = BehaviorRuleEngine(wildcardRules)

            Then("Double wildcard ** matches nested door paths") {
                val rule = wildcardEngine.findRule("Vehicle.Cabin.Door.Row1.Left.Window.Position")
                rule shouldBe wildcardRules[0]
                val rule2 = wildcardEngine.findRule("Vehicle.Cabin.Door.Row2.Right.Window.Position")
                rule2 shouldBe wildcardRules[0]
            }

            Then("Single wildcard * matches exactly one path component") {
                val rule = wildcardEngine.findRule("Vehicle.Body.Lights.Beam.High.IsOn")
                rule shouldBe wildcardRules[1]
                wildcardEngine.findRule("Vehicle.Body.Lights.Beam.High.Left.IsOn") shouldBe null
            }

            Then("Target sensor defaults to actual actuator path when omitted") {
                val rule = wildcardEngine.findRule("Vehicle.Cabin.Door.Row1.Left.Window.Position")!!
                val targetSensor = wildcardEngine.resolveTargetSensor(
                    rule,
                    "Vehicle.Cabin.Door.Row1.Left.Window.Position",
                )
                targetSensor shouldBe "Vehicle.Cabin.Door.Row1.Left.Window.Position"
            }
        }

        When("Resolving unconfigured actuator paths") {
            val emptyEngine = BehaviorRuleEngine(emptyList())

            Then("Fallback direct reflection rule is automatically synthesized") {
                val rule = emptyEngine.resolveRule("Vehicle.Cabin.Seat.Row1.DriverSide.Heating")
                rule.actuator shouldBe "Vehicle.Cabin.Seat.Row1.DriverSide.Heating"
                rule.transform shouldBe Transform.DIRECT
                rule.delayMs shouldBe 0

                val targetSensor = emptyEngine.resolveTargetSensor(rule, "Vehicle.Cabin.Seat.Row1.DriverSide.Heating")
                targetSensor shouldBe "Vehicle.Cabin.Seat.Row1.DriverSide.Heating"
            }
        }
    }
})
