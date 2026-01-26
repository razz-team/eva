package com.razz.eva.repository

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.jooq.record.BaseEntityRecord
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Select
import org.jooq.Table

abstract class JooqBaseEntityRepository<E : CreatableEntity, R : BaseEntityRecord>(
    private val queryExecutor: QueryExecutor,
    private val dslContext: DSLContext,
    private val table: Table<R>,
) : EntityRepository<E> {

    protected abstract fun toRecord(entity: E): R

    protected abstract fun fromRecord(record: R): E

    /**
     * Derive the unique condition to identify this entity in the database.
     * Typically based on composite primary key or unique constraint columns.
     */
    protected abstract fun entityCondition(entity: E): Condition

    override suspend fun add(context: TransactionalContext, entity: E): E {
        val record = toRecord(entity)
        val insertQuery = dslContext.insertQuery(table).apply {
            setRecord(record)
        }
        val added = queryExecutor.executeStore(
            dslContext = dslContext,
            jooqQuery = insertQuery,
            table = table,
        ).singleOrNull()
        @Suppress("UNCHECKED_CAST")
        return when (added) {
            null -> throw IllegalStateException("Insert returned unexpected result")
            else -> fromRecord(added)
        }
    }

    override suspend fun add(context: TransactionalContext, entities: List<E>): List<E> {
        if (entities.isEmpty()) throw IllegalArgumentException("No entities provided for insert")
        if (entities.size == 1) return listOf(add(context, entities.first()))
        val insertQuery = entities.fold(dslContext.insertQuery(table)) { query, entity ->
            val record = toRecord(entity)
            query.newRecord()
            query.setRecord(record)
            query
        }
        val added = queryExecutor.executeStore(
            dslContext = dslContext,
            jooqQuery = insertQuery,
            table = table,
        )
        if (added.size != entities.size) {
            throw IllegalStateException(
                "${entities.size} entities queried for insert, ${added.size} inserted",
            )
        }
        return added.map(::fromRecord)
    }

    protected suspend fun listAllWhere(condition: Condition, limit: Int = MAX_RETURNED_RECORDS): List<E> {
        val select = dslContext.selectFrom(table).where(condition).limit(limit)
        return allRecords(select).map(::fromRecord)
    }

    protected suspend fun findOneWhere(condition: Condition): E? {
        val select = dslContext.selectFrom(table).where(condition)
        return atMostOneRecord(select)?.let(::fromRecord)
    }

    protected suspend fun existsWhere(condition: Condition): Boolean {
        val select = dslContext.selectOne().from(table).where(condition)
        return atMostOneRecord(dslContext.selectOne().whereExists(select)) != null
    }

    protected suspend fun <R : Record> atMostOneRecord(select: Select<R>): R? {
        val records = allRecords(select)
        return when (records.size) {
            0 -> null
            1 -> records.first()
            else -> throw JooqQueryException(
                query = select,
                records = records,
                message = "Found more than one record: ${records.size}. Type: ${select.recordType}",
            )
        }
    }

    protected suspend fun <R : Record> allRecords(select: Select<R>): List<R> {
        return queryExecutor.executeSelect(
            dslContext = dslContext,
            jooqQuery = select,
            table = select.asTable(),
        )
    }

    private companion object {
        private const val MAX_RETURNED_RECORDS = 1000
    }
}
