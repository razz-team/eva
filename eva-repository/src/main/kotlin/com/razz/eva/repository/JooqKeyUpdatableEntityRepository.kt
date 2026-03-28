package com.razz.eva.repository

import com.razz.eva.domain.UpdatableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.jooq.record.BaseEntityRecord
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Table

@Suppress("INAPPLICABLE_JVM_NAME")
abstract class JooqKeyUpdatableEntityRepository<E : UpdatableEntity, K : EntityKey<E>, R : BaseEntityRecord>(
    private val queryExecutor: QueryExecutor,
    private val dslContext: DSLContext,
    private val table: Table<R>,
) : JooqUpdatableEntityRepository<E, R>(queryExecutor, dslContext, table),
    KeyUpdatable<E, K> {

    abstract fun keyCondition(key: K): Condition

    override suspend fun update(context: TransactionalContext, key: K): Boolean {
        val updateQuery = dslContext.updateQuery(table).apply {
            addConditions(keyCondition(key))
        }
        val updated = queryExecutor.executeQuery(
            dslContext = dslContext,
            jooqQuery = updateQuery,
        )
        return updated > 0
    }

    @JvmName("updateByKeys")
    override suspend fun update(context: TransactionalContext, keys: List<K>): Int {
        if (keys.isEmpty()) return 0
        if (keys.size == 1) return if (update(context, keys.first())) 1 else 0
        val conditions = keys.map(::keyCondition).reduce(Condition::or)
        val updateQuery = dslContext.updateQuery(table).apply {
            addConditions(conditions)
        }
        val updated = queryExecutor.executeQuery(
            dslContext = dslContext,
            jooqQuery = updateQuery,
        )
        return updated
    }
}
