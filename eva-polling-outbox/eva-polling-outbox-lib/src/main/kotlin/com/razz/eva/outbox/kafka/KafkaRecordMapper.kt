package com.razz.eva.outbox.kafka

import com.razz.eva.outbox.OutboxEvent
import com.razz.eva.serialization.json.UuidSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Instant
import java.util.UUID

class KafkaRecordMapper(
    private val topicResolver: TopicResolver,
) {
    private val logger = KotlinLogging.logger {}

    fun toRecord(event: OutboxEvent): ProducerRecord<String, ByteArray> {
        val value = EventValue(
            id = event.id,
            uowId = event.uowId,
            modelId = event.modelId,
            modelName = event.modelName,
            name = event.name,
            occurredAt = event.occurredAt,
            payload = event.payload,
            tracingContext = tracingContext(event),
        )
        return ProducerRecord(
            topicResolver.topic(event),
            event.modelId,
            json.encodeToString(value).toByteArray(Charsets.UTF_8),
        )
    }

    // consumers decode it into a string map, so a malformed context is dropped instead of breaking them
    private fun tracingContext(event: OutboxEvent): String? = try {
        json.decodeFromString<Map<String, String>>(event.tracingContext)
        event.tracingContext
    } catch (ex: SerializationException) {
        logger.warn(ex) { "Malformed tracing context of event ${event.id}, publishing without it" }
        null
    } catch (ex: IllegalArgumentException) {
        logger.warn(ex) { "Malformed tracing context of event ${event.id}, publishing without it" }
        null
    }

    @Serializable
    private data class EventValue(
        @SerialName("id") @Serializable(with = UuidSerializer::class) val id: UUID,
        @SerialName("uow_id") @Serializable(with = UuidSerializer::class) val uowId: UUID,
        @SerialName("model_id") val modelId: String,
        @SerialName("model_name") val modelName: String,
        @SerialName("name") val name: String,
        @SerialName("occurred_at") @Serializable(with = EpochMillisSerializer::class) val occurredAt: Instant,
        @SerialName("payload") val payload: String,
        @SerialName("tracing_context") val tracingContext: String?,
    )

    private companion object {
        val json = Json
    }
}
