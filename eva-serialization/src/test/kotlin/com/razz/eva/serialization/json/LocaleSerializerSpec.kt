package com.razz.eva.serialization.json

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.*

class LocaleSerializerSpec : BehaviorSpec({

    Given("Locale has language and country code") {
        val template = Template(Locale("fr", "CH"))

        lateinit var templateJson: String

        When("Principal encodes locale to json") {
            templateJson = JsonFormat.json.encodeToString(template)

            Then("Correct json will be created") {
                templateJson shouldBe """{"locale":"fr-CH"}"""
            }
        }

        When("Principal decodes template") {
            val decodedTemplate = JsonFormat.json.decodeFromString<Template>(templateJson)

            Then("Original template should be returned") {
                decodedTemplate shouldBe template
            }
        }
    }
})
