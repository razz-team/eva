package com.razz.eva.events

import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.util.*
import java.util.UUID.randomUUID

data class IntegrationModelEvent(
    val id: EventId,
    val eventName: EventName,
    val uowId: UowId,
    val modelId: ModelId,
    val modelName: ModelName,
    val occurredAt: Instant,
    val payload: JsonObject,
    val metadata: Map<String, String> = emptyMap(),
) {

    data class EventId(private val id: UUID) {
        override fun toString() = id.toString()

        fun toUUID() = id

        companion object {
            fun random() = EventId(randomUUID())

            fun fromString(id: String) = EventId(UUID.fromString(id))
        }
    }

    data class UowId(private val id: UUID) {
        override fun toString() = id.toString()

        fun toUUID() = id

        companion object {
            fun random() = UowId(randomUUID())
        }
    }

    data class ModelId(private val id: String) {
        fun stringValue() = id

        override fun toString() = id
    }

    data class EventName(private val name: String) {
        override fun toString() = name
    }

    data class ModelName(private val name: String) {
        override fun toString() = name
    }

    override fun toString(): String {
        return "PersistedModelEvent(" +
            "id=$id, " +
            "eventName=$eventName, " +
            "uowId=$uowId, " +
            "modelId=$modelId, " +
            "modelName=$modelName, " +
            "occurredAt=$occurredAt" +
            "metadata=$metadata" +
            ")"
    }
}
