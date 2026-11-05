package com.razz.eva.uow.params.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.razz.eva.uow.guarded.Guarded
import com.razz.eva.uow.guarded.Proven

/**
 * The audit row of a guarded unit of work, as the kotlinx serializer writes it: a [Guarded] as
 * `{"action", "target", "params"}`, the params with the mapper's own serializer, and a [Proven] as
 * `{"action", "target"}`. Without it Jackson would write the proof by its getters.
 */
internal object GuardedAuditModule : SimpleModule("eva-guarded-audit") {

    init {
        addSerializer(Guarded::class.java, GuardedAudit)
        addSerializer(Proven::class.java, ProvenAudit)
    }

    private object GuardedAudit : StdSerializer<Guarded<*, *, *>>(Guarded::class.java) {

        override fun serialize(value: Guarded<*, *, *>, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeStartObject()
            gen.writeStringField("action", value.action)
            gen.writeStringField("target", value.targetId)
            gen.writeFieldName("params")
            provider.defaultSerializeValue(value.params, gen)
            gen.writeEndObject()
        }
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
