package com.razz.eva.uow.params.kotlinx

import com.razz.eva.uow.guarded.Guarded
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure

/** The audit row of a [Guarded]: the action, the target's id, and the unit of work's own params under `params`. */
internal class GuardedAudit(
    private val params: SerializationStrategy<Any?>,
) : SerializationStrategy<Guarded<*, *, *>> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.razz.eva.uow.guarded.Guarded") {
        element<String>("action")
        element<String>("target")
        element("params", params.descriptor)
    }

    override fun serialize(encoder: Encoder, value: Guarded<*, *, *>) = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.action)
        encodeStringElement(descriptor, 1, value.targetId)
        encodeSerializableElement(descriptor, 2, params, value.params)
    }
}
