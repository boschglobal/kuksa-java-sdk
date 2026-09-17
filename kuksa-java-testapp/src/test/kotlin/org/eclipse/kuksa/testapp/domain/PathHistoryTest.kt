package org.eclipse.kuksa.testapp.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class PathHistoryTest : BehaviorSpec({
    Given("A PathHistory with maxSize 20 (UT-11)") {
        val history = PathHistory(maxSize = 20)

        When("Recording unique paths") {
            history.record("Vehicle.Speed")
            history.record("Vehicle.Cabin.Door.Row1.Left.IsOpen")

            Then("Most recent path is first") {
                history.toList() shouldBe listOf(
                    "Vehicle.Cabin.Door.Row1.Left.IsOpen",
                    "Vehicle.Speed",
                )
            }
        }

        When("Recording duplicate path") {
            history.record("Vehicle.Speed")

            Then("Duplicate is moved to the top") {
                history.toList() shouldBe listOf(
                    "Vehicle.Speed",
                    "Vehicle.Cabin.Door.Row1.Left.IsOpen",
                )
            }
        }

        When("Recording more than maxSize entries") {
            for (i in 1..25) {
                history.record("Vehicle.Signal.$i")
            }

            Then("Size is capped at 20 and oldest entries evicted") {
                val list = history.toList()
                list shouldHaveSize 20
                list.first() shouldBe "Vehicle.Signal.25"
                list.last() shouldBe "Vehicle.Signal.6"
            }
        }
    }
})
