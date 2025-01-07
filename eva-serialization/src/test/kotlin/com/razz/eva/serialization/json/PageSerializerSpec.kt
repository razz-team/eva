package com.razz.eva.serialization.json

import com.razz.eva.paging.Page
import com.razz.eva.paging.Size
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.encodeToString

class PageSerializerSpec : BehaviorSpec({
    val clock = Clock.fixed(Instant.parse("2021-01-01T00:00:00Z"), Clock.systemUTC().zone)

    Given("Result with pages") {
        val result = Result(
            pureFirst = Page.firstPage(Size(1000)),
            commonFirst = Page.firstPage(Size(1001)),
            pureNext = Page.Next(
                maxOrdering = clock.instant(),
                offset = clock.instant().plus(10, ChronoUnit.DAYS).toString(),
                size = Size(2000)
            ),
            commonNext = Page.Next(
                maxOrdering = clock.instant(),
                offset = clock.instant().plus(11, ChronoUnit.DAYS).toString(),
                size = Size(2001)
            ),
        )

        When("Principal encodes value to json") {
            val json = JsonFormat.json.encodeToString(result)

            Then("Json with masked embedded fields will be created") {
                json shouldEqualJson """
                  {
                      "pureFirst": {
                        "size": 1000
                      },
                      "pureNext": {
                        "maxOrdering": "2021-01-01T00:00:00.000Z",
                        "offset": "2021-01-11T00:00:00Z",
                        "size": 2000
                      },
                      "commonNext": {
                        "type": "next",
                        "maxOrdering": "2021-01-01T00:00:00.000Z",
                        "offset": "2021-01-12T00:00:00Z",
                        "size": 2001
                      },
                      "commonFirst": {
                        "type": "first",
                        "size": 1001
                      }
                  }
                """
            }
        }

        When("Principal encodes value to json and decodes it back") {
            val json = JsonFormat.json.encodeToString(result)
            val actual = JsonFormat.json.decodeFromString(Result.serializer(), json)

            Then("Decoded value should be equal to original") {
                actual shouldBe result
            }
        }
    }
})
