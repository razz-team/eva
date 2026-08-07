package com.razz.eva.uow.params.kotlinx

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Wraps a params field whose value must never reach the uow_events.params audit column.
 * The serializer emits a redaction marker instead of the value, so "never stored" is a type
 * instead of a comment. The wrapped value stays available in memory via [value] for the perform.
 *
 * For fields which are large rather than secret, prefer @Transient with a default value:
 * a transient field is skipped entirely and the audit row stays smaller.
 */
@Serializable(with = Sensitive.Serializer::class)
class Sensitive<T>(val value: T) {

    override fun toString(): String = REDACTED

    class Serializer<T>(
        @Suppress("unused") private val valueSerializer: KSerializer<T>,
    ) : KSerializer<Sensitive<T>> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("com.razz.eva.uow.params.kotlinx.Sensitive", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Sensitive<T>) = encoder.encodeString(REDACTED)

        override fun deserialize(decoder: Decoder): Sensitive<T> =
            error("Sensitive value was redacted at serialization and can not be deserialized")
    }

    companion object {
        private const val REDACTED = "***"
    }
}
