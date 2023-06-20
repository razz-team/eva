package com.razz.jooq.converter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.sql.Timestamp
import java.time.Instant

class InstantConverterTest : FunSpec({

    val converter = InstantConverter()

    test("Millis should not be added") {
        val instantWithMillis = Instant.parse("2021-06-04T15:54:30.123Z")
        val timestampWithMillis = converter.to(instantWithMillis)!!
        timestampWithMillis.nanos shouldBe 123_000_000
    }

    test("Zone should not be changed") {
        val instant = Instant.parse("2022-06-04T15:54:30.123Z")
        val timestamp = converter.to(instant)
        timestamp shouldBe Timestamp.valueOf("2022-06-04 15:54:30.123")
        val reverseInstant = converter.from(timestamp)
        reverseInstant shouldBe instant
    }
})
