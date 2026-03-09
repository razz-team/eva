package com.razz.eva.repository

import com.razz.eva.domain.ModelState.PersistentState
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.paging.Page
import com.razz.eva.paging.PagedList
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.jooq.record.BaseModelRecord
import java.time.Instant
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Select
import org.jooq.SelectConditionStep
import org.jooq.SortField
import org.jooq.Table
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

abstract class JooqBaseModelRepository<ID, MID, M, ME, R>(
    queryExecutor: QueryExecutor,
    private val dslContext: DSLContext,
    private val table: Table<R>,
    @Suppress("UNCHECKED_CAST")
    private val tableId: TableField<R, ID> = requireNotNull(table.primaryKey).fields.single() as TableField<R, ID>,
    @Suppress("UNCHECKED_CAST")
    private val dbId: (MID) -> ID = { mid -> mid.id as ID },
    @Suppress("UNCHECKED_CAST")
    version: TableField<R, Long> = table.recordVersion as TableField<R, Long>,
    @Suppress("UNCHECKED_CAST")
    createdAt: TableField<R, Instant> = table.field("record_created_at") as TableField<R, Instant>,
    stripNotModifiedFields: Boolean = false,
) : AbstractJooqRepository<ID, MID, M, ME, R>(
    queryExecutor, dslContext, table, tableId, dbId, version, createdAt, stripNotModifiedFields,
), ModelRepository<MID, M>
    where ID : Comparable<ID>,
          MID : ModelId<out Comparable<*>>,
          M : Model<MID, ME>,
          ME : ModelEvent<MID>,
          R : BaseModelRecord<ID> {

    private fun fromRecord(record: R): M {
        return fromRecord(record, persistentState(record))
    }

    protected abstract fun fromRecord(record: R, modelState: PersistentState<MID, ME>): M

    /**
     * This method meant to be used only by
     * [com.razz.eva.uow.ModelPersisting#add]
     * sadly we don't have a way (yet) to encapsulate it
     * for test purposes use
     * @see [com.razz.eva.uow.ModelPersisting]
     */
    override suspend fun <ME : M> add(context: TransactionalContext, model: ME): ME {
        val record = persistRecord(context, model)
        @Suppress("UNCHECKED_CAST")
        return fromRecord(record) as ME
    }

    override suspend fun <ME : M> add(context: TransactionalContext, models: List<ME>): List<ME> {
        return persistRecords(context, models).map {
            @Suppress("UNCHECKED_CAST")
            fromRecord(it) as ME
        }
    }

    /**
     * This method meant to be used only by
     * [com.razz.eva.uow.ModelPersisting#update]
     * sadly we don't have a way (yet) to encapsulate it
     * for test purposes use
     * @see [com.razz.eva.uow.ModelPersisting]
     */
    override suspend fun <ME : M> update(context: TransactionalContext, model: ME): ME {
        val record = updateRecord(context, model)
        @Suppress("UNCHECKED_CAST")
        return fromRecord(record) as ME
    }

    override suspend fun <ME : M> update(context: TransactionalContext, models: List<ME>): List<ME> {
        return updateRecords(context, models).map {
            @Suppress("UNCHECKED_CAST")
            fromRecord(it) as ME
        }
    }

    override suspend fun find(id: MID): M? {
        return findOneWhere(tableId.eq(dbId(id)))
    }

    override suspend fun list(ids: Collection<MID>): List<M> {
        val uniqueDbIds = ids.toSet().map { dbId(it) }
        return when {
            uniqueDbIds.isEmpty() -> listOf()
            uniqueDbIds.size <= 3 -> {
                findAllWhere(tableId.`in`(uniqueDbIds))
            }
            else -> {
                val idParams = uniqueDbIds.map<ID, Field<ID>>(DSL::`val`).toTypedArray()
                findAllWhere(tableId.eq(DSL.any(*idParams)))
            }
        }
    }

    protected suspend fun existsWhere(condition: Condition): Boolean {
        val select = dslContext.selectOne()
            .from(table)
            .where(condition)
        return exists(select)
    }

    private suspend fun <R : Record> exists(select: Select<R>): Boolean {
        return atMostOneRecord(dslContext.selectOne().whereExists(select)) != null
    }

    protected suspend fun findOneWhere(condition: Condition): M? {
        val select = dslContext.selectFrom(table)
            .where(condition)
        return findOne(select)
    }

    private suspend fun findOne(select: Select<R>): M? {
        return atMostOneRecord(select)?.let(::fromRecord)
    }

    protected suspend fun findLast(
        condition: Condition,
        sortField: TableField<R, *>,
        sortFields: Array<TableField<R, *>> = emptyArray(),
    ): M? = findAllWhere(
        condition = condition,
        sortField = sortField.desc(),
        sortFields = sortFields.map(TableField<R, *>::desc).toTypedArray(),
        limit = 1,
    ).singleOrNull()

    /**
     * If page is not first, we use JOOQ 'seek' method to find position of page
     * https://www.jooq.org/doc/latest/manual/sql-building/sql-statements/select-statement/seek-clause/
     *
     * Example of generated SQL query -
     * SELECT id, timestamp, value
     * FROM model
     * WHERE (timestamp, id) > (X, Y)
     * ORDER BY timestamp, id
     * LIMIT 5
     *
     * (timestamp, id) > (X, Y) is equivalent to (timestamp > X) OR ((timestamp = X) AND (id > Y))
     */
    protected suspend fun <N, S, P> findPage(
        condition: Condition,
        page: Page<P>,
        pagingStrategy: PagingStrategy<ID, N, S, P, R>,
        mapper: (R) -> N = {
            @Suppress("UNCHECKED_CAST")
            fromRecord(it) as N
        },
    ): PagedList<S, P> where S : N, P : Comparable<P> {
        val list = allRecords(
            dslContext.selectFrom(table)
                .where(condition)
                .page(page, pagingStrategy),
        )
        return pagingStrategy.pagedList(list, mapper, page.size)
    }

    private fun <N, S, P> SelectConditionStep<R>.page(
        page: Page<P>,
        pagingStrategy: PagingStrategy<ID, N, S, P, R>,
    ) where S : N, P : Comparable<P> = pagingStrategy.select(this, page)

    protected suspend fun findAllWhere(
        condition: Condition,
        sortField: SortField<*>? = null,
        sortFields: Array<SortField<*>> = emptyArray(),
        limit: Int = MAX_RETURNED_RECORDS,
    ): List<M> {
        val select = dslContext.selectFrom(table)
            .where(condition)
        return if (sortField != null) {
            findAll(select.orderBy(sortField, *sortFields).limit(limit))
        } else {
            findAll(select.limit(limit))
        }
    }

    protected suspend fun findAll(select: Select<R>): List<M> {
        return allRecords(select).map(::fromRecord)
    }

    protected suspend fun count(condition: Condition): Long {
        return atMostOneRecord(
            dslContext.select(LONG_COUNT).from(table).where(condition),
        )?.value1() ?: 0
    }

    protected suspend fun count(condition: Condition, limit: Long): Long {
        return atMostOneRecord(
            dslContext.select(LONG_COUNT).from(
                dslContext.selectOne()
                    .from(table)
                    .where(condition)
                    .limit(limit),
            ),
        )?.value1() ?: 0
    }

    protected suspend fun countGrouped(condition: Condition, groupFields: Set<TableField<R, *>>): Long {
        return atMostOneRecord(
            dslContext
                .select(LONG_COUNT)
                .from(
                    dslContext.select(groupFields)
                        .from(table)
                        .where(condition)
                        .groupBy(groupFields),
                ),
        )?.value1() ?: 0
    }

    private companion object {
        private const val MAX_RETURNED_RECORDS = 1000

        private val LONG_COUNT = DSL.field("count(*)", SQLDataType.BIGINT)
    }
}
