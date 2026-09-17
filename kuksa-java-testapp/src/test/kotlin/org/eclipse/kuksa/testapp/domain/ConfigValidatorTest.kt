package org.eclipse.kuksa.testapp.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import org.eclipse.kuksa.testapp.model.ConnectionConfig

class ConfigValidatorTest : BehaviorSpec({
    Given("A ConfigValidator") {
        When("Validating a valid config") {
            val config = ConnectionConfig(
                host = "localhost",
                port = 55556,
                jwt = null,
                useTls = false,
            )
            val result = ConfigValidator.validate(config)

            Then("It returns ValidationResult.Ok") {
                result shouldBe ValidationResult.Ok
            }
        }

        When("Validating an empty host (UT-1)") {
            val config = ConnectionConfig(
                host = "   ",
                port = 55556,
                jwt = null,
                useTls = false,
            )
            val result = ConfigValidator.validate(config)

            Then("It returns ValidationResult.Error with invalid host message") {
                result shouldBe instanceOf(ValidationResult.Error::class)
                (result as ValidationResult.Error).message shouldBe "Host cannot be blank"
            }
        }

        When("Validating an invalid port <= 0 or > 65535 (UT-2)") {
            val configZero = ConnectionConfig(host = "localhost", port = 0)
            val configOverflow = ConnectionConfig(host = "localhost", port = 70000)

            Then("It returns ValidationResult.Error for invalid ports") {
                ConfigValidator.validate(configZero) shouldBe instanceOf(ValidationResult.Error::class)
                ConfigValidator.validate(configOverflow) shouldBe instanceOf(ValidationResult.Error::class)
            }
        }
    }
})
