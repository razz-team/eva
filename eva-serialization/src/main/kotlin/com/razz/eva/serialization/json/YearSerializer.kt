package com.razz.eva.serialization.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind.INT
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Year

object YearSerializer : KSerializer<Year> {

    override val descriptor = PrimitiveSerialDescriptor("Year", INT)

    override fun deserialize(decoder: Decoder): Year = Year.of(decoder.decodeInt())

    override fun serialize(encoder: Encoder, value: Year) = encoder.encodeInt(value.value)
}
