package com.razz.eva.serialization.json

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZoneId

class ZoneIdSerializerSpec : BehaviorSpec({

    Given("The timezone") {
        val agent = Agent(ZoneId.of("America/Argentina/Buenos_Aires"))

        lateinit var agentJson: String

        When("Principal encodes timezone to json") {
            agentJson = JsonFormat.json.encodeToString(agent)

            Then("Correct json will be created") {
                agentJson shouldBe """{"timezone":"America/Argentina/Buenos_Aires"}"""
            }
        }

        When("Principal decodes agent") {
            val decodedAgent = JsonFormat.json.decodeFromString<Agent>(agentJson)

            Then("Original agent should be returned") {
                decodedAgent shouldBe agent
            }
        }
    }
})
