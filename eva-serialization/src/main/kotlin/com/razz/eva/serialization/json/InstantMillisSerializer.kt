package com.razz.serialization.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind.STRING
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object InstantMillisSerializer : KSerializer<Instant> {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    override val descriptor = PrimitiveSerialDescriptor("Instant", STRING)

    override fun deserialize(decoder: Decoder): Instant =
        Instant.parse(decoder.decodeString()).truncatedTo(ChronoUnit.MILLIS)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(formatToString(value))

    fun formatToString(value: Instant): String = formatter.format(value.atZone(UTC))

    fun parseFromString(value: String): Instant = Instant.parse(value)
}
