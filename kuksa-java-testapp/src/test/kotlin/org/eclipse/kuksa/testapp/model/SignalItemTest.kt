package org.eclipse.kuksa.testapp.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.eclipse.kuksa.vsscore.model.VssSignal

class SignalItemTest : BehaviorSpec({
    Given("A VssSignal instance (UT-4)") {
        val testSignal = object : VssSignal<Float> {
            override val uuid: String = "uuid-123"
            override val vssPath: String = "Vehicle.Speed"
            override val description: String = "Vehicle speed"
            override val type: String = "sensor"
            override val comment: String = ""
            override val value: Float = 0.0f
        }

        When("Creating SignalItem from VssNode") {
            val item = SignalItem.fromVssNode(testSignal)

            Then("It extracts vssPath, type, and dataType") {
                item.vssPath shouldBe "Vehicle.Speed"
                item.signalType shouldBe "sensor"
                item.dataType shouldBe "Float"
            }
        }
    }
})
