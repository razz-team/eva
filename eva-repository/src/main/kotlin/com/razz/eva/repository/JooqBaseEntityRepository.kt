package com.razz.eva.repository

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.paging.Page
import com.razz.eva.paging.PagedList
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

    /**
     * Keyset-paginated read for entity repositories. See [executeFindPage] for the SQL shape.
     */
    protected suspend fun <ID : Comparable<ID>, N, S, P> findPage(
        condition: Condition,
        page: Page<P>,
        pagingStrategy: PagingStrategy<ID, N, S, P, R>,
        mapper: (R) -> N = {
            @Suppress("UNCHECKED_CAST")
            fromRecord(it) as N
        },
    ): PagedList<S, P> where S : N, P : Comparable<P> = executeFindPage(
        queryExecutor = queryExecutor,
        dslContext = dslContext,
        table = table,
        condition = condition,
        page = page,
        pagingStrategy = pagingStrategy,
        mapper = mapper,
    )

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
