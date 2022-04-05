package com.razz.eva.serialization.json

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class JsonQuerySpec : BehaviorSpec({

    Given("Json object") {
        val json = Json.decodeFromString<JsonObject>(
            """
            {
                "string": "value",
                "object": {"key": "value"},
                "array": ["value1","value2"],
                "int": 1,
                "long": 1,
                "double": 1.1,
                "float": 1.1,
                "boolean": true,
                "stringOpt": null,
                "objectOpt": null,
                "arrayOpt": null,
                "intOpt": null,
                "longOpt": null,
                "doubleOpt": null,
                "floatOpt": null,
                "booleanOpt": null
            }
        """
        )

        When("Principal gets object from json") {
            val result = json.jsonObject("object")

            Then("Result is correct") {
                result shouldBe buildJsonObject { put("key", "value") }
            }
        }

        When("Principal gets string from json") {
            val result = json.string("string")

            Then("Result is correct") {
                result shouldBe "value"
            }
        }

        When("Principal gets array from json") {
            val result = json.jsonArray("array")

            Then("Result is correct") {
                result shouldBe buildJsonArray {
                    add("value1")
                    add("value2")
                }
            }
        }

        When("Principal gets int from json") {
            val result = json.int("int")

            Then("Result is correct") {
                result shouldBe 1
            }
        }

        When("Principal gets long from json") {
            val result = json.long("long")

            Then("Result is correct") {
                result shouldBe 1L
            }
        }

        When("Principal gets double from json") {
            val result = json.double("double")

            Then("Result is correct") {
                result shouldBe 1.1
            }
        }

        When("Principal gets float from json") {
            val result = json.float("float")

            Then("Result is correct") {
                result shouldBe 1.1f
            }
        }

        When("Principal gets bool from json") {
            val result = json.boolean("boolean")

            Then("Result is correct") {
                result shouldBe true
            }
        }

        When("Principal gets objectOpt from json") {
            val result = json.jsonObjectOpt("objectOpt")

            Then("Result is correct") {
                result shouldBe null
            }
        }

        When("Principal gets stringOpt from json") {
            val result = json.stringOpt("stringOpt")

            Then("Result is correct") {
                result shouldBe null
            }
        }

        When("Principal gets array from json") {
            val result = json.jsonArrayOpt("arrayOpt")

            Then("Result is correct") {
                result shouldBe null
            }
        }

        When("Principal gets intOpt from json") {
            val result = json.intOpt("intOpt")

            Then("Result is correct") {
                result shouldBe null
            }
        }

        When("Principal gets longOpt from json") {
            val result = json.longOpt("longOpt")

            Then("Result is correct") {
                result shouldBe null
            }
        }

        When("Principal gets doubleOpt from json") {
            val result = json.doubleOpt("doubleOpt")

            Then("Result is correct") {
                result shouldBe null
            }
        }

        When("Principal gets floatOpt from json") {
            val result = json.floatOpt("floatOpt")

            Then("Result is correct") {
                result shouldBe null
            }
        }

        When("Principal gets boolOpt from json") {
            val result = json.booleanOpt("booleanOpt")

            Then("Result is correct") {
                result shouldBe null
            }
        }
    }
})
