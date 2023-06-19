package com.razz.jooq.converter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

class InstantConverterTest : FunSpec({

    val converter = InstantConverter()

    test("Millis should not be added") {
        val instantWithMillis = Instant.parse("2021-06-04T15:54:30.123Z")
        val timestampWithMillis = converter.to(instantWithMillis)!!
        timestampWithMillis shouldBe Timestamp.valueOf(LocalDateTime.ofInstant(instantWithMillis, UTC))
        timestampWithMillis.nanos shouldBe 123_000_000
    }
})
