package com.razz.eva.serialization.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind.STRING
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

object LocaleSerializer : KSerializer<Locale> {

    override val descriptor = PrimitiveSerialDescriptor("Locale", STRING)

    override fun deserialize(decoder: Decoder): Locale = Locale.forLanguageTag(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Locale) = encoder.encodeString(value.toLanguageTag())
}
