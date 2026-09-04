package org.eclipse.kuksa.testapp.repository

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class VssSignalRepositoryTest : BehaviorSpec({
    Given("The VssSignalRepository") {
        When("Retrieving all signals") {
            val signals = VssSignalRepository.allSignals()

            Then("It returns more than 50 signals with valid paths") {
                signals.size shouldBeGreaterThan 50
                signals.all { it.vssPath.isNotBlank() } shouldBe true
            }

            Then("It contains common signals like Vehicle.Speed") {
                signals.any { it.vssPath == "Vehicle.Speed" } shouldBe true
            }
        }

        When("Retrieving sensor signals") {
            val sensors = VssSignalRepository.sensors()

            Then("It returns sensors and only sensors") {
                sensors.size shouldBeGreaterThan 0
                sensors.all { it.signalType.equals("sensor", ignoreCase = true) } shouldBe true
                sensors.any { it.vssPath == "Vehicle.Speed" } shouldBe true
            }
        }

        When("Retrieving actuator signals") {
            val actuators = VssSignalRepository.actuators()

            Then("It returns actuators and only actuators") {
                actuators.size shouldBeGreaterThan 0
                actuators.all { it.signalType.equals("actuator", ignoreCase = true) } shouldBe true
            }
        }
    }
})
