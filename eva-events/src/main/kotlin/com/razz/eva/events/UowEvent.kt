package com.razz.eva.events

import com.razz.eva.IdempotencyKey
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.Principal
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.util.*
import java.util.UUID.randomUUID

data class UowEvent(
    val id: Id,
    val uowName: UowName,
    val principal: Principal<*>,
    val modelEvents: Map<ModelEventId, ModelEvent<*>>,
    val idempotencyKey: IdempotencyKey?,
    val params: JsonElement,
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
