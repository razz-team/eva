package com.razz.eva.repository

import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.jooq.record.BaseEntityRecord
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Table

@Suppress("INAPPLICABLE_JVM_NAME")
abstract class JooqKeyDeletableEntityRepository<E : DeletableEntity, K : EntityKey<E>, R : BaseEntityRecord>(
    private val queryExecutor: QueryExecutor,
    private val dslContext: DSLContext,
    private val table: Table<R>,
) : JooqDeletableEntityRepository<E, R>(queryExecutor, dslContext, table),
    KeyDeletable<E, K> {

    abstract fun keyCondition(key: K): Condition

    override suspend fun delete(
        context: TransactionalContext,
        key: K,
    ): Boolean {
        val deleteQuery = dslContext.deleteFrom(table)
            .where(keyCondition(key))
        val deleted = queryExecutor.executeQuery(
            dslContext = dslContext,
            jooqQuery = deleteQuery,
        )
        return deleted > 0
    }

    @JvmName("deleteByKeys")
    override suspend fun delete(
        context: TransactionalContext,
        keys: List<K>,
    ): Int {
        if (keys.isEmpty()) return 0
        if (keys.size == 1) return if (delete(context, keys.first())) 1 else 0
        val conditions = keys.map(::keyCondition).reduce(Condition::or)
        val deleteQuery = dslContext.deleteFrom(table).where(conditions)
        val deleted = queryExecutor.executeQuery(
            dslContext = dslContext,
            jooqQuery = deleteQuery,
        )
        return deleted
    }
}
