package com.razz.eva.persistence

import com.razz.eva.IdempotencyKey
import com.razz.eva.domain.ModelId
import java.util.UUID

sealed class PersistenceException(message: String) : RuntimeException(message) {

    sealed interface ConstraintViolation {
        val constraintName: String?
    }

    open class UniqueModelRecordViolationException(
        val modelId: ModelId<*>,
        val tableName: String,
        override val constraintName: String?,
    ) : ConstraintViolation, PersistenceException(
        "Uniqueness of [$tableName]:[${modelId.stringValue()}]" +
            " violated ${constraintName?.let { ": [$it]" }}"
    )

    open class ModelRecordConstraintViolationException(
        val modelId: ModelId<*>,
        val tableName: String,
        override val constraintName: String?,
    ) : ConstraintViolation, PersistenceException(
        "Constraint for [$tableName]:[${modelId.stringValue()}]" +
            " violated ${constraintName?.let { ": [$it]" }}"
    )

    class UniqueUowEventRecordViolationException(
        val uowId: UUID,
        val uowName: String,
        val idempotencyKey: IdempotencyKey?,
        val constraintName: String?
    ) : PersistenceException(
        "Uniqueness of [$uowName] [$uowId] ${idempotencyKey?.let { ": [${it.stringValue()}]" }}" +
            " violated ${constraintName?.let { ": [$it]" }}"
    )

    class StaleRecordException(
        val modelIds: Set<ModelId<*>>,
        val tableName: String,
    ) : PersistenceException(
        $$"Rows for $${
            modelIds.joinToString(
                prefix = "[",
                postfix = "]"
            ) { modelId -> tableName + ": " + modelId.stringValue() }
        } were concurrently updated"
    ) {
        constructor(modelId: ModelId<*>, tableName: String) : this(setOf(modelId), tableName)
    }

    class ModelPersistingGenericException(
        modelId: ModelId<*>,
        override val cause: Throwable,
    ) : PersistenceException("Persisting [${modelId.stringValue()}] failed")

    class PersistingGenericException(
        override val cause: Throwable,
    ) : PersistenceException("Persisting failed")

    class EventPayloadTooLargeException(
        val modelId: ModelId<*>,
        val modelEventId: UUID,
        val eventId: UUID,
        val payloadSize: Int,
        val maxEventPayloadSize: Int,
    ) : PersistenceException(
        "Event [eventId=$eventId, modelEventId=$modelEventId], modelId=$modelId " +
            "payload size is $payloadSize which exceeds " +
            "maxEventPayloadSize $maxEventPayloadSize bytes"
    )
}
