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
        override val constraintName: String?
    ) : ConstraintViolation, PersistenceException(
        "Uniqueness of [$tableName]:[${modelId.stringValue()}]" +
            " violated ${constraintName?.let { ": [$it]" }}"
    )

    open class ModelRecordConstraintViolationException(
        val modelId: ModelId<*>,
        val tableName: String,
        override val constraintName: String?
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
        val modelIds: Set<ModelId<*>>
    ) : PersistenceException(
        "Rows for ${modelIds.joinToString(prefix = "[", postfix = "]") { it.stringValue() }} were concurrently updated"
    ) {
        constructor(modelId: ModelId<*>) : this(setOf(modelId))
    }

    class ModelPersistingGenericException(
        val modelId: ModelId<*>,
        override val cause: Throwable
    ) : PersistenceException("Persisting [${modelId.stringValue()}] failed")

    class PersistingGenericException(
        override val cause: Throwable
    ) : PersistenceException("Persisting failed")
}
