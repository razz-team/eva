package com.razz.eva.serialization.json

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.math.BigDecimal

class BigDecimalSerializerSpec : BehaviorSpec({

    listOf(
        "135.69",
        "0.0",
    ).forAll {
        Given("The $it big decimal") {
            val bigDecimal = BigDecimal(it)

            lateinit var bigDecimalJson: String

            When("Principal encodes big decimal to json") {
                bigDecimalJson = JsonFormat.json.encodeToString(bigDecimal)

                Then("Correct json will be created") {
                    bigDecimalJson shouldBe """"$it""""
                }
            }

            When("Principal decodes big decimal") {
                val decodedBigDecimal = JsonFormat.json.decodeFromString<BigDecimal>(bigDecimalJson)

                Then("Original big decimal should be returned") {
                    decodedBigDecimal shouldBe bigDecimal
                }
            }
        }
    }
})
