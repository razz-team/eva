package com.razz.eva.repository

import com.razz.eva.domain.DeletableEntity
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.jooq.record.BaseEntityRecord
import org.jooq.DSLContext
import org.jooq.Table

abstract class JooqDeletableEntityRepository<E : DeletableEntity, R : BaseEntityRecord>(
    private val queryExecutor: QueryExecutor,
    private val dslContext: DSLContext,
    private val table: Table<R>,
) : JooqBaseEntityRepository<E, R>(queryExecutor, dslContext, table),
    DeletableEntityRepository<E> {

    override suspend fun delete(context: TransactionalContext, entity: E): Boolean {
        val deleteQuery = dslContext.deleteFrom(table)
            .where(entityCondition(entity))
        val deleted = queryExecutor.executeQuery(
            dslContext = dslContext,
            jooqQuery = deleteQuery,
        )
        return deleted > 0
    }

    override suspend fun delete(context: TransactionalContext, entities: List<E>): Int {
        if (entities.isEmpty()) return 0
        if (entities.size == 1) return if (delete(context, entities.first())) 1 else 0
        val conditions = entities.map { entityCondition(it) }
            .reduce { acc, cond -> acc.or(cond) }
        val deleteQuery = dslContext.deleteFrom(table).where(conditions)
        val deleted = queryExecutor.executeQuery(
            dslContext = dslContext,
            jooqQuery = deleteQuery,
        )
        return deleted
    }
}
