package com.razz.eva.serialization.json

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.LocalDate

class LocalDateSerializerSpec : BehaviorSpec({

    Given("The local date") {
        val localDate = LocalDate.of(2020, 1, 1)

        lateinit var localDateJson: String

        When("Principal encodes local date to json") {
            localDateJson = JsonFormat.json.encodeToString(localDate)

            Then("Correct json will be created") {
                localDateJson shouldBe """"2020-01-01""""
            }
        }

        When("Principal decodes local date") {
            val decodedLocalDate = JsonFormat.json.decodeFromString<LocalDate>(localDateJson)

            Then("Original local date should be returned") {
                decodedLocalDate shouldBe localDate
            }
        }
    }
})
