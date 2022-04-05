package com.razz.eva.serialization.json

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.*

class UuidSerializerSpec : BehaviorSpec({

    Given("User has id with UUID format") {
        val user = User(UUID.fromString("5188202c-807a-4056-afaf-365446177554"))

        lateinit var userJson: String

        When("Principal encodes User to json") {
            userJson = JsonFormat.json.encodeToString(user)

            Then("Correct json will be created") {
                userJson shouldBe """{"id":"5188202c-807a-4056-afaf-365446177554"}"""
            }
        }

        When("Principal decodes User") {
            val decodedUser = JsonFormat.json.decodeFromString<User>(userJson)

            Then("Original user should be returned") {
                decodedUser shouldBe user
            }
        }
    }
})
