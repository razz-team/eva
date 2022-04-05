package com.razz.eva.serialization.json

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

class LocalDateTimeSecondsSerializerSpec : BehaviorSpec({
    val serializer = JsonFormat.json

    Given("The local date time") {
        val localDateTime = Instant.ofEpochSecond(1607251212).atZone(UTC).toLocalDateTime()

        lateinit var localDateJson: String

        When("Principal encodes local date time to json") {
            localDateJson = serializer.encodeToString(localDateTime)

            Then("Correct json will be created") {
                localDateJson shouldBe """"2020-12-06T10:40:12""""
            }
        }

        When("Principal decodes local date time") {
            val decodedLocalDate = serializer.decodeFromString<LocalDateTime>(localDateJson)

            Then("Original local date time should be returned") {
                decodedLocalDate shouldBe localDateTime
            }
        }
    }

    Given("Local date time with nanosecond precision") {
        val localDateTime = Instant.ofEpochSecond(1607251212).atZone(UTC).toLocalDateTime().plusNanos(123)

        lateinit var localDateJson: String

        When("Principal encodes local date time to json") {
            localDateJson = serializer.encodeToString(localDateTime)

            Then("Correct json will be created") {
                localDateJson shouldBe """"2020-12-06T10:40:12""""
            }
        }

        When("Principal decodes local date time") {
            val decodedLocalDate = serializer.decodeFromString<LocalDateTime>(localDateJson)

            Then("Original local date time should be truncated to seconds") {
                decodedLocalDate shouldBe LocalDateTime.ofEpochSecond(1607251212, 0, UTC)
            }
        }
    }

    Given("Json local date time with nanosecond precision") {
        val localDateTimeJson = """"2020-12-06T10:40:12.666000123""""

        lateinit var decodedLocalDate: LocalDateTime

        When("Principal decodes local date time") {
            decodedLocalDate = serializer.decodeFromString(localDateTimeJson)

            Then("Correct json will be created") {
                decodedLocalDate shouldBe LocalDateTime.ofEpochSecond(1607251212, 0, UTC)
            }
        }

        When("Principal encodes local date time") {
            val encodedJson = serializer.encodeToString(decodedLocalDate)

            Then("Original local date time should be truncated to seconds") {
                encodedJson shouldBe """"2020-12-06T10:40:12""""
            }
        }
    }
})
