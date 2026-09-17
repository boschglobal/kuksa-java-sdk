package org.eclipse.kuksa.mockprovider.discovery

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.eclipse.kuksa.vsscore.model.VssNode
import org.eclipse.kuksa.vsscore.model.VssSignal
import java.io.File
import kotlin.reflect.KClass

private class TestActuator(
    override val vssPath: String = "Vehicle.Cabin.Door.Row1.Left.IsOpen",
    override val type: String = "actuator",
    override val value: Boolean = false,
) : VssSignal<Boolean> {
    override val uuid: String = "actuator-uuid"
    override val description: String = "Door actuator"
    override val comment: String = ""
    override val dataType: KClass<*> get() = Boolean::class
}

private class TestSensor(
    override val vssPath: String = "Vehicle.Speed",
    override val type: String = "sensor",
    override val value: Float = 0f,
) : VssSignal<Float> {
    override val uuid: String = "sensor-uuid"
    override val description: String = "Speed sensor"
    override val comment: String = ""
    override val dataType: KClass<*> get() = Float::class
}

private class TestVehicle(
    private val actuator: TestActuator = TestActuator(),
    private val sensor: TestSensor = TestSensor(),
) : VssNode {
    override val uuid: String = "Vehicle"
    override val vssPath: String = "Vehicle"
    override val description: String = "Vehicle Root"
    override val type: String = "branch"
    override val comment: String = ""
    override val children: Set<VssNode> get() = setOf(actuator, sensor)
}

class VssActuatorDiscoveryTest : BehaviorSpec({
    Given("VssActuatorDiscovery") {
        When("Discovering actuators from VSS Node hierarchy fallback") {
            val actuators = VssActuatorDiscovery.discoverFromNode(TestVehicle())

            Then("Discovered actuators list contains only actuator signals") {
                actuators.size shouldBe 1
                actuators shouldContain "Vehicle.Cabin.Door.Row1.Left.IsOpen"
            }
        }

        When("Discovering actuators dynamically from vss/ directory") {
            val actuators = VssActuatorDiscovery.discoverActuatorPaths(vssDirectory = File("vss"))

            Then("Extracts full actuator paths from definition files") {
                actuators.size shouldBeGreaterThan 0
                actuators shouldContain "Vehicle.ADAS.CruiseControl.SpeedSet"
            }
        }

        When("Discovering from non-existent directory") {
            val actuators = VssActuatorDiscovery.discoverActuatorPaths(
                vssDirectory = File("invalid_non_existent_dir"),
                fallbackRoot = TestVehicle(),
            )

            Then("Falls back gracefully to fallbackRoot") {
                actuators.size shouldBe 1
                actuators shouldContain "Vehicle.Cabin.Door.Row1.Left.IsOpen"
            }
        }
    }
})
