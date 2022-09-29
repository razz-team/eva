package com.razz.eva.serialization.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind.STRING
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration

object DurationSerializer : KSerializer<Duration> {

    override val descriptor = PrimitiveSerialDescriptor("DurationString", STRING)

    override fun deserialize(decoder: Decoder): Duration = Duration.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeString(value.toString())
}
