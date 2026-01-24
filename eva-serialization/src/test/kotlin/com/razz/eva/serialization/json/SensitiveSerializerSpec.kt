package com.razz.eva.serialization.json

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.UUID.randomUUID

@Serializable(with = SensitiveSerializer::class)
data class Password(val value: String)

@Serializable
data class Params(
    val password: Password,
    val openText: String,
)

class SensitiveSerializerSpec : BehaviorSpec({

    listOf(
        URI("whatever"),
        randomUUID(),
        "plain string",
        listOf(0L, object {}),
    ).forAll {
        Given("The $it big decimal") {

            When("Principal encodes value to json") {
                val json = JsonFormat.json.encodeToString(SensitiveSerializer, it)

                Then("Masked json will be created") {
                    json shouldBe """"***""""
                }
            }
        }
    }

    Given("A value with embedded sensitive value") {
        val params = Params(Password("qwerty"), "gaSGH@E!QWGl;nF_%12")

        When("Principal encodes value to json") {
            val json = JsonFormat.json.encodeToString(params)

            Then("Json with masked embedded fields will be created") {
                json shouldBe """{"password":"***","openText":"gaSGH@E!QWGl;nF_%12"}"""
            }
        }
    }
})
