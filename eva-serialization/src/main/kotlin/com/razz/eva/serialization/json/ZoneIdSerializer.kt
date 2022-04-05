package com.razz.eva.serialization.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind.STRING
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.ZoneId

object ZoneIdSerializer : KSerializer<ZoneId> {

    override val descriptor = PrimitiveSerialDescriptor("ZoneId", STRING)

    override fun deserialize(decoder: Decoder): ZoneId = ZoneId.of(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: ZoneId) = encoder.encodeString(value.id)
}
