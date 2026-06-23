package com.razz.eva.persistence

import com.razz.eva.IdempotencyKey
import com.razz.eva.domain.ModelId
import java.util.UUID

sealed class PersistenceException(message: String) : RuntimeException(message) {

    sealed interface ModelAware {
        val modelIds: Set<ModelId<*>>
    }

    sealed interface ConstraintViolation {
        val constraintName: String?
    }

    sealed interface TableAware {
        val tableName: String
    }

    open class UniqueModelRecordViolationException(
        val modelId: ModelId<*>,
        override val tableName: String,
        override val constraintName: String?,
    ) : ConstraintViolation, ModelAware, TableAware, PersistenceException(
        "Uniqueness of [$tableName]:[${modelId.stringValue()}]" +
            " violated ${constraintName?.let { ": [$it]" }}",
    ) {
        override val modelIds: Set<ModelId<*>>
            get() = setOf(modelId)
    }

    open class ModelRecordConstraintViolationException(
        val modelId: ModelId<*>,
        override val tableName: String,
        override val constraintName: String?,
    ) : ConstraintViolation, ModelAware, TableAware, PersistenceException(
        "Constraint for [$tableName]:[${modelId.stringValue()}]" +
            " violated ${constraintName?.let { ": [$it]" }}",
    ) {
        override val modelIds: Set<ModelId<*>>
            get() = setOf(modelId)
    }

    class UniqueUowEventRecordViolationException(
        val uowId: UUID,
        val uowName: String,
        val idempotencyKey: IdempotencyKey?,
        val constraintName: String?,
    ) : PersistenceException(
        "Uniqueness of [$uowName] [$uowId] ${idempotencyKey?.let { ": [${it.stringValue()}]" }}" +
            " violated ${constraintName?.let { ": [$it]" }}",
    )

    class StaleRecordException(
        override val modelIds: Set<ModelId<*>>,
        override val tableName: String,
    ) : ModelAware, TableAware, PersistenceException(
        $$"Rows for $${
            modelIds.joinToString(
                prefix = "[",
                postfix = "]",
            ) { modelId -> tableName + ": " + modelId.stringValue() }
        } were concurrently updated",
    ) {
        constructor(modelId: ModelId<*>, tableName: String) : this(setOf(modelId), tableName)
    }

    class ModelPersistingGenericException(
        val modelId: ModelId<*>,
        override val cause: Throwable,
    ) : ModelAware, PersistenceException("Persisting [${modelId.stringValue()}] failed") {
        override val modelIds: Set<ModelId<*>>
            get() = setOf(modelId)
    }

    class PersistingGenericException(
        override val cause: Throwable,
    ) : PersistenceException("Persisting failed")

    class EventPayloadTooLargeException(
        val modelId: ModelId<*>,
        val modelEventId: UUID,
        val eventId: UUID,
        val payloadSize: Int,
        val maxEventPayloadSize: Int,
    ) : ModelAware, PersistenceException(
        "Event [eventId=$eventId, modelEventId=$modelEventId], modelId=$modelId " +
            "payload size is $payloadSize which exceeds " +
            "maxEventPayloadSize $maxEventPayloadSize bytes",
    ) {
        override val modelIds: Set<ModelId<*>>
            get() = setOf(modelId)
    }
}
