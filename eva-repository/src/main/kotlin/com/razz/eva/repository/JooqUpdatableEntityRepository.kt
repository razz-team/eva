package com.razz.eva.repository

import com.razz.eva.domain.UpdatableEntity
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.jooq.record.BaseEntityRecord
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Table

abstract class JooqUpdatableEntityRepository<E : UpdatableEntity, R : BaseEntityRecord>(
    private val queryExecutor: QueryExecutor,
    private val dslContext: DSLContext,
    private val table: Table<R>,
) : JooqBaseEntityRepository<E, R>(queryExecutor, dslContext, table),
    UpdatableEntityRepository<E> {

    /**
     * Derive the unique condition to identify this entity in the database.
     * Typically based on composite primary key or unique constraint columns.
     */
    protected abstract fun entityCondition(entity: E): Condition

    override suspend fun update(context: TransactionalContext, entity: E): E {
        val record = toRecord(entity)
        val updateQuery = dslContext.updateQuery(table).apply {
            setRecord(record)
            addConditions(entityCondition(entity))
        }
        val updated = queryExecutor.executeStore(
            dslContext = dslContext,
            jooqQuery = updateQuery,
            table = table,
        )
        return when (updated.size) {
            0 -> throw IllegalStateException("Entity not found for update")
            1 -> fromRecord(updated.first())
            else -> throw IllegalStateException(
                "Update affected ${updated.size} rows, expected 1",
            )
        }
    }

    override suspend fun update(context: TransactionalContext, entities: List<E>): List<E> {
        if (entities.isEmpty()) throw IllegalArgumentException("No entities provided for update")
        if (entities.size == 1) return listOf(update(context, entities.first()))
        return entities.map { update(context, it) }
    }
}
