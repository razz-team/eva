package com.razz.eva.uow.params.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.razz.eva.uow.guarded.Proven

/**
 * Makes Jackson write a [Proven] as the kotlinx serializer does, `{"action": ..., "target": ...}`. Without it Jackson
 * writes the `targetId` getter under its own name. It handles [Proven] only: do not put a
 * [com.razz.eva.uow.guarded.Proof] itself in params, Jackson would write all its properties.
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
