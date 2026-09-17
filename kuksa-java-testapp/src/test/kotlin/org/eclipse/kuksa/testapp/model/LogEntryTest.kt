package org.eclipse.kuksa.testapp.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain

class LogEntryTest : BehaviorSpec({
    Given("A LogEntry instance (UT-3)") {
        val entry = LogEntry(
            timestamp = 1700000000000L,
            direction = Direction.REQUEST,
            apiMethod = "fetchValue",
            payload = "signal_id: Vehicle.Speed",
        )

        When("Formatting entry") {
            val formatted = entry.format()

            Then("It contains direction, api method, and payload") {
                formatted shouldContain "[REQUEST]"
                formatted shouldContain "fetchValue"
                formatted shouldContain "signal_id: Vehicle.Speed"
            }
        }
    }
})
