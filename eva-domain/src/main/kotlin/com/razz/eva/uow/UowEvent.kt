package com.razz.eva.uow

import com.razz.eva.domain.ModelEvent
import com.razz.eva.uow.params.UowParams
import java.time.Instant
import java.util.*
import java.util.UUID.randomUUID

data class UowEvent<Params : UowParams<Params>>(
    val id: Id,
    val uowName: UowName,
    val principal: Principal<*>,
    val modelEvents: Map<ModelEventId, ModelEvent<*>>,
    val params: Params,
    val occurredAt: Instant,
) {
    @JvmInline
    value class Id(private val id: UUID) {
        override fun toString() = id.toString()
        fun uuidValue() = id

        companion object {
            fun random() = Id(randomUUID())
        }
    }

    @JvmInline
    value class ModelEventId(private val id: UUID) {
        override fun toString() = id.toString()
        fun uuidValue() = id

        companion object {
            fun random() = ModelEventId(randomUUID())
        }
    }

    @JvmInline
    value class UowName(private val name: String) {
        override fun toString() = name
        fun stringValue() = name
    }
}
