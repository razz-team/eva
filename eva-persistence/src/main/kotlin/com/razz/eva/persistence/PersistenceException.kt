package com.razz.eva.persistence

import com.razz.eva.IdempotencyKey
import com.razz.eva.domain.ModelId
import java.util.UUID

sealed class PersistenceException(message: String) : RuntimeException(message) {

    class UniqueModelRecordViolationException(
        val modelId: ModelId<*>,
        tableName: String,
        val constraintName: String?
    ) : PersistenceException(
        "Uniqueness of [$tableName]:[${modelId.stringValue()}]" +
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

    class ModelRecordConstraintViolationException(
        val modelId: ModelId<*>,
        tableName: String,
        val constraintName: String?
    ) : PersistenceException(
        "Constraint for [$tableName]:[${modelId.stringValue()}]" +
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
