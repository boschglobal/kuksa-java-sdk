package org.eclipse.kuksa.mockprovider.discovery

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

class VssDefinitionParserTest : BehaviorSpec({
    Given("A VssDefinitionParser") {
        When("Parsing actuators from flat YAML content") {
            val yaml = """
                Vehicle.ADAS.CruiseControl.SpeedSet:
                  datatype: float
                  description: Set cruise control speed.
                  type: actuator
                Vehicle.ADAS.CruiseControl.IsError:
                  datatype: boolean
                  description: Error indicator.
                  type: sensor
                Vehicle.Cabin.Door.Row1.Left.IsOpen:
                  datatype: boolean
                  type: actuator
            """.trimIndent()

            val actuators = VssDefinitionParser.parseYamlActuators(yaml)

            Then("Only signals with type actuator are extracted") {
                actuators.size shouldBe 2
                actuators shouldContain "Vehicle.ADAS.CruiseControl.SpeedSet"
                actuators shouldContain "Vehicle.Cabin.Door.Row1.Left.IsOpen"
            }
        }

        When("Parsing actuators from nested JSON tree content") {
            val json = """
                {
                  "Vehicle": {
                    "type": "branch",
                    "children": {
                      "Speed": {
                        "type": "sensor",
                        "datatype": "float"
                      },
                      "ADAS": {
                        "type": "branch",
                        "children": {
                          "CruiseControl": {
                            "type": "branch",
                            "children": {
                              "SpeedSet": {
                                "type": "actuator",
                                "datatype": "float"
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            val actuators = VssDefinitionParser.parseJsonActuators(json)

            Then("Only actuator leaves with full dot-separated paths are extracted") {
                actuators.size shouldBe 1
                actuators shouldContain "Vehicle.ADAS.CruiseControl.SpeedSet"
            }
        }

        When("Parsing actual workspace vss/ files") {
            val vssDir = File("vss")
            if (vssDir.exists()) {
                val actuators = VssDefinitionParser.parseActuatorsFromDirectory(vssDir)

                Then("Discovers hundreds of actuators from the files") {
                    actuators.size shouldBeGreaterThan 100
                    actuators shouldContain "Vehicle.ADAS.CruiseControl.SpeedSet"
                }
            }
        }

        When("Parsing non-existent or empty files") {
            val empty = VssDefinitionParser.parseActuatorsFromFile(File("non_existent_file.yaml"))

            Then("Returns empty list gracefully without throwing exception") {
                empty shouldBe emptyList()
            }
        }
    }
})
