package com.razz.eva.outbox.app

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration

/**
 * ISO-8601 duration, e.g. `PT10S` or `PT0.01S`.
 */
internal object IsoDurationSerializer : KSerializer<Duration> {
    override val descriptor = PrimitiveSerialDescriptor("IsoDuration", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Duration = Duration.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeString(value.toString())
}
