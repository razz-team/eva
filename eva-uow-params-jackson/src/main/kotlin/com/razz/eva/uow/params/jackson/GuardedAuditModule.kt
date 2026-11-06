package com.razz.eva.uow.params.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.razz.eva.uow.guarded.Proven

/**
 * A [Proven] in params as the kotlinx serializer writes it, `{"action", "target"}`: without it Jackson would write
 * the proof by its getters. It writes [Proven] only: a raw [com.razz.eva.uow.guarded.Proof] in params is written
 * unredacted.
 */
internal object GuardedAuditModule : SimpleModule("eva-guarded-audit") {

    init {
        addSerializer(Proven::class.java, ProvenAudit)
    }

    private object ProvenAudit : StdSerializer<Proven<*, *>>(Proven::class.java) {

        override fun serialize(value: Proven<*, *>, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeStartObject()
            gen.writeStringField("action", value.action)
            gen.writeStringField("target", value.targetId)
            gen.writeEndObject()
        }
    }
}
