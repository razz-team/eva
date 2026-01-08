package com.razz.eva.serialization.json

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class InstantMillisSerializerSpec : BehaviorSpec({
    val serializer = JsonFormat.json

    Given("Instant with millisecond precision") {
        val instant = Instant.ofEpochMilli(1607251212666)

        lateinit var instantJson: String

        When("Principal encodes instant to json") {
            instantJson = serializer.encodeToString(instant)

            Then("Correct json will be created") {
                instantJson shouldBe """"2020-12-06T10:40:12.666Z""""
            }
        }

        When("Principal decodes instant") {
            val decodedInstant = serializer.decodeFromString<Instant>(instantJson)

            Then("Original instant should be returned") {
                decodedInstant shouldBe instant
            }
        }
    }

    Given("Instant with nanosecond precision") {
        val instant = Instant.ofEpochMilli(1607251212666).plusNanos(123)

        lateinit var instantJson: String

        When("Principal encodes instant to json") {
            instantJson = serializer.encodeToString(instant)

            Then("Correct json will be created") {
                instantJson shouldBe """"2020-12-06T10:40:12.666Z""""
            }
        }

        When("Principal decodes instant") {
            val decodedInstant = serializer.decodeFromString<Instant>(instantJson)

            Then("Original instant should be truncated to millis") {
                decodedInstant shouldBe Instant.ofEpochMilli(1607251212666)
            }
        }
    }

    Given("Json instant with nanosecond precision") {
        val instantJson = """"2020-12-06T10:40:12.666000123Z""""

        lateinit var decodedInstant: Instant

        When("Principal decodes instant") {
            decodedInstant = serializer.decodeFromString(instantJson)

            Then("Correct json will be created") {
                decodedInstant shouldBe Instant.ofEpochMilli(1607251212666)
            }
        }

        When("Principal encodes instant") {
            val encodedJson = serializer.encodeToString(decodedInstant)

            Then("Original instant should be truncated to millis") {
                encodedJson shouldBe """"2020-12-06T10:40:12.666Z""""
            }
        }
    }
})
