package com.razz.eva.serialization.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object SensitiveSerializer : KSerializer<Any> {

    override val descriptor = PrimitiveSerialDescriptor("sensitive", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Any) {
        encoder.encodeString("***")
    }

    override fun deserialize(decoder: Decoder): Nothing {
        throw IllegalStateException("You can't read sensitive value, because it wasn't serializable")
    }
}
