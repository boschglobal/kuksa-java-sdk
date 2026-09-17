package org.eclipse.kuksa.testapp.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DataTypeSerializerTest : BehaviorSpec({
    Given("A DataTypeSerializer") {
        When("Converting float string to v1 Datapoint and v2 Value (UT-8)") {
            val v1Dp = DataTypeSerializer.toDatapoint("float", "123.45")
            val v2Val = DataTypeSerializer.toValue("float", "123.45")

            Then("Values are converted correctly") {
                v1Dp.float shouldBe 123.45f
                v2Val.float shouldBe 123.45f
            }
        }

        When("Converting boolean string (UT-9)") {
            val v1Dp = DataTypeSerializer.toDatapoint("boolean", "true")
            val v2Val = DataTypeSerializer.toValue("boolean", "false")

            Then("Values are converted correctly") {
                v1Dp.bool shouldBe true
                v2Val.bool shouldBe false
            }
        }

        When("Converting integer types") {
            val v1Dp = DataTypeSerializer.toDatapoint("int32", "42")
            val v2Val = DataTypeSerializer.toValue("uint32", "100")
            val v2Val64 = DataTypeSerializer.toValue("int64", "9999999999")

            Then("Values are converted correctly") {
                v1Dp.int32 shouldBe 42
                v2Val.uint32 shouldBe 100
                v2Val64.int64 shouldBe 9999999999L
            }
        }

        When("Converting string type") {
            val v1Dp = DataTypeSerializer.toDatapoint("string", "test-value")
            val v2Val = DataTypeSerializer.toValue("string", "test-value")

            Then("Values are string") {
                v1Dp.string shouldBe "test-value"
                v2Val.string shouldBe "test-value"
            }
        }

        When("Parsing invalid float string (UT-10)") {
            Then("It throws DataTypeSerializationException") {
                shouldThrow<DataTypeSerializationException> {
                    DataTypeSerializer.toDatapoint("float", "invalid-float")
                }
                shouldThrow<DataTypeSerializationException> {
                    DataTypeSerializer.toValue("int32", "abc")
                }
            }
        }
    }
})
