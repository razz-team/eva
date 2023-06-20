package com.razz.jooq.converter

import org.jooq.Converter
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

class InstantConverter : Converter<Timestamp, Instant> {

    override fun from(timestamp: Timestamp?): Instant? {
        val localDateTime = timestamp?.toLocalDateTime()
        return localDateTime?.toInstant(UTC)
    }

    override fun to(instant: Instant?): Timestamp? {
        val localDateTime = LocalDateTime.ofInstant(instant, UTC)
        return Timestamp.valueOf(localDateTime)
    }

    override fun fromType(): Class<Timestamp> {
        return Timestamp::class.java
    }

    override fun toType(): Class<Instant> {
        return Instant::class.java
    }

    companion object {
        val instance = InstantConverter()
    }
}
