package com.razz.eva.serialization.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind.STRING
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.URL

object URLSerializer : KSerializer<URL> {

    override val descriptor = PrimitiveSerialDescriptor("URL", STRING)

    override fun deserialize(decoder: Decoder): URL = URL(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: URL) = encoder.encodeString(value.toString())
}
