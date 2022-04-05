package com.razz.eva

import com.razz.eva.IdempotencyKey.Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

@Serializable(with = Serializer::class)
data class IdempotencyKey private constructor(val value: String) {

    fun stringValue() = value

    companion object {
        fun random() = idempotencyKey(UUID.randomUUID())
        fun idempotencyKey(value: String) = IdempotencyKey(value)
        fun idempotencyKey(value: UUID) = IdempotencyKey(value.toString())
    }

    object Serializer : KSerializer<IdempotencyKey> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IdempotencyKey", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: IdempotencyKey) {
            encoder.encodeString(value.stringValue())
        }

        override fun deserialize(decoder: Decoder): IdempotencyKey {
            return idempotencyKey(decoder.decodeString())
        }
    }
}
