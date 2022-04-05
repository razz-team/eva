package com.razz.eva.serialization.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind.STRING
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit.SECONDS

object LocalDateTimeSecondsSerializer : KSerializer<LocalDateTime> {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", STRING)

    override fun deserialize(decoder: Decoder): LocalDateTime =
        LocalDateTime.parse(decoder.decodeString()).truncatedTo(SECONDS)

    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(formatToString(value))

    fun formatToString(value: LocalDateTime): String = formatter.format(value.atZone(UTC))
}
