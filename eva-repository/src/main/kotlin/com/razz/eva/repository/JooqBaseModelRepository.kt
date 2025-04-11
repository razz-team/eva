package com.razz.eva.repository

import com.razz.eva.domain.EntityState.PersistentState
import com.razz.eva.domain.EntityState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Version.Companion.version
import com.razz.eva.paging.Page
import com.razz.eva.paging.PagedList
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.jooq.record.BaseEntityRecord
import io.vertx.pgclient.PgException
import java.time.Instant
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Select
import org.jooq.SelectConditionStep
import org.jooq.SortField
import org.jooq.StoreQuery
import org.jooq.Table
import org.jooq.TableField
import org.jooq.UpdateQuery
import org.jooq.exception.DataAccessException
import org.jooq.exception.SQLStateClass
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

abstract class JooqBaseModelRepository<ID, MID, M, ME, R>(
    private val queryExecutor: QueryExecutor,
    private val dslContext: DSLContext,
    private val table: Table<R>,
    @Suppress("UNCHECKED_CAST")
    private val tableId: TableField<R, ID> = requireNotNull(table.primaryKey).fields.single() as TableField<R, ID>,
    @Suppress("UNCHECKED_CAST")
    private val dbId: (MID) -> ID = { mid -> mid.id as ID },
    @Suppress("UNCHECKED_CAST")
    private val version: TableField<R, Long> = table.recordVersion as TableField<R, Long>,
    @Suppress("UNCHECKED_CAST")
    private val createdAt: TableField<R, Instant> = table.field("record_created_at") as TableField<R, Instant>,
) : ModelRepository<MID, M>
    where ID : Comparable<ID>,
          MID : ModelId<out Comparable<*>>,
          M : Model<MID, ME>,
          ME : ModelEvent<MID>,
          R : BaseEntityRecord<ID> {

    private fun toRecord(context: TransactionalContext, model: M): R {
        return modelBaseToRecord(context, toRecord(model), model)
    }

    internal open fun modelBaseToRecord(context: TransactionalContext, record: R, model: M): R {
        return record.apply {
            setRecordUpdatedAt(context.startedAt)
            if (model.isNew()) {
                setId(dbId(model.id()))
                setRecordCreatedAt(context.startedAt)
            } else {
                reset(tableId)
                reset(createdAt)
            }
            setVersion(model.version().version.inc())
        }
    }

    private fun fromRecord(record: R): M {
        return fromRecord(
            record,
            persistentState(
                version(record.getVersion()!!)
            )
        )
    }

    protected abstract fun toRecord(model: M): R

    protected abstract fun fromRecord(record: R, entityState: PersistentState<MID, ME>): M

    private fun Table<*>.onlyModifiableFields(): Array<Field<*>> = this.fields().filterNot { field ->
        field.unqualifiedName == createdAt.unqualifiedName || field.unqualifiedName == tableId.unqualifiedName
    }.toTypedArray()

    private val ORIGIN_ALIAS = DSL.quotedName("T")
    private val ORIGIN_TABLE = table.`as`(ORIGIN_ALIAS)
    private val VALUES_ALIAS = DSL.quotedName("U")
    private val VALUES_ROW = table.fields().filter { createdAt != it }.map { it.unqualifiedName }.toTypedArray()

    private val destinationValues = DSL.row(*table.onlyModifiableFields())
    private val sourceValues = DSL.row(*ORIGIN_TABLE.`as`(VALUES_ALIAS).onlyModifiableFields())

    /**
     * This method meant to be used only by
     * [com.razz.eva.uow.ModelPersisting#add]
     * sadly we don't have a way (yet) to encapsulate it
     * for test purposes use
     * @see [com.razz.eva.uow.ModelPersisting]
     */
    override suspend fun <ME : M> add(context: TransactionalContext, model: ME): ME {
        require(model.isNew()) {
            "Can insert only new model"
        }
        val insertQuery = prepareQuery(context, model, dslContext.insertQuery(table))
        val added = wrapException(model) {
            queryExecutor.executeStore(
                dslContext = dslContext,
                jooqQuery = insertQuery,
                table = table
            )
        }.singleOrNull()

        @Suppress("UNCHECKED_CAST")
        when (added) {
            null -> throw IllegalStateException("Too many rows updated")
            else -> return fromRecord(added) as ME
        }
    }

    override suspend fun <ME : M> add(context: TransactionalContext, models: List<ME>): List<ME> {
        when {
            models.isEmpty() -> throw IllegalArgumentException("No models provided for insert")
            models.size == 1 -> {
                // just for optimization's sake, this version is capable of handling single record update
                val added = add(context, models.first())
                return listOf(added)
            }
        }
        val insertQuery = models.fold(dslContext.insertQuery(table)) { query, model ->
            require(model.isNew()) {
                "Can insert only new model"
            }
            query.newRecord()
            prepareQuery(context, model, query)
        }
        val added = wrapException(models.first()) {
            queryExecutor.executeStore(
                dslContext = dslContext,
                jooqQuery = insertQuery,
                table = table
            )
        }

        if (added.size != models.size) {
            throw IllegalStateException(
                "${models.size} models were queried for insert, while ${added.size} rows were inserted"
            )
        }
        return added.map {
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
        require(model.isDirty()) {
            "Can update only dirty model"
        }
        val updateQuery = prepareUpdate(model, prepareQuery(context, model, dslContext.updateQuery(table)))
        val updated = wrapException(model) {
            queryExecutor.executeStore(
                dslContext = dslContext,
                jooqQuery = updateQuery,
                table = table
            )
        }.getSingleOrNull(this::fromRecord) {
            val type = model::class
            JooqQueryException(updateQuery, it, "Too many rows updated. Type: $type")
        }

        @Suppress("UNCHECKED_CAST")
        when (updated) {
            null -> throw PersistenceException.StaleRecordException(model.id())
            else -> return updated as ME
        }
    }

    override suspend fun <ME : M> update(context: TransactionalContext, models: List<ME>): List<ME> {
        when {
            models.isEmpty() -> throw IllegalArgumentException("No models provided for update")
            models.size == 1 -> {
                // just for optimization sake, this version is capable of handling single record update
                val updated = update(context, models.first())
                return listOf(updated)
            }
        }
        val records = models.map { model ->
            require(model.isDirty()) {
                "Can update only dirty model"
            }
            DSL.row(
                *toRecord(context, model).run {
                    setId(dbId(model.id()))
                    valuesRow()
                        .fields()
                        .filterIndexed { i, _ -> createdAt != this.field(i) }
                        .map { field -> field.cast(field.dataType) }
                        .toTypedArray()
                }
            )
        }.toTypedArray()
        val updateQuery = dslContext.updateQuery(ORIGIN_TABLE).apply {
            addValues(destinationValues, sourceValues)
            addFrom(DSL.values(*records).`as`(VALUES_ALIAS, *VALUES_ROW))
        }.let(::prepareUpdate)

        val updated = wrapException(models.first()) {
            queryExecutor.executeStore(
                dslContext = dslContext,
                jooqQuery = updateQuery,
                table = table
            )
        }

        when {
            updated.size < models.size -> {
                val notUpdated = models.mapTo(mutableSetOf(), Model<*, *>::id)
                    .subtract(updated.mapTo(mutableSetOf()) { fromRecord(it).id() })
                throw PersistenceException.StaleRecordException(notUpdated)
            }
            updated.size > models.size -> throw IllegalStateException(
                "Only ${models.size} models were queried for update, while ${updated.size} rows were updated"
            )
        }
        return updated.map {
            @Suppress("UNCHECKED_CAST")
            fromRecord(it) as ME
        }
    }

    private fun <Q : StoreQuery<R>> prepareQuery(context: TransactionalContext, model: M, storeQuery: Q): Q {
        val record = toRecord(context, model)
        storeQuery.setRecord(record)
        return storeQuery
    }

    private fun <Q : UpdateQuery<R>> prepareUpdate(updateQuery: Q): Q {
        return updateQuery.apply {
            addConditions(
                DSL.field(DSL.name(VALUES_ALIAS, tableId.unqualifiedName)).cast(tableId.dataType).eq(
                    DSL.field(DSL.name(ORIGIN_ALIAS, tableId.unqualifiedName)).cast(tableId.dataType)
                ),
                DSL.field(DSL.name(VALUES_ALIAS, version.unqualifiedName)).cast(version.dataType).eq(
                    DSL.field(DSL.name(ORIGIN_ALIAS, version.unqualifiedName)).cast(version.dataType).plus(1)
                ),
                // TODO map `partitionCond` to aliased fields
            )
        }
    }

    private fun <Q : UpdateQuery<R>> prepareUpdate(model: M, updateQuery: Q): Q {
        updateQuery.addConditions(tableId.eq(dbId(model.id())))
        updateQuery.addConditions(version.eq(model.version().version))
        updateQuery.addConditions(partitionCond(model))
        return updateQuery
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
        return atMostOneRecord(select)?.let { fromRecord(it) }
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
                .page(page, pagingStrategy)
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
        return allRecords(select)
            .map { fromRecord(it) }
    }

    protected suspend fun count(condition: Condition): Long {
        return atMostOneRecord(dslContext.select(LONG_COUNT).from(table).where(condition))
            ?.value1() ?: 0
    }

    protected suspend fun countGrouped(condition: Condition, groupFields: Set<TableField<R, *>>): Long {
        return atMostOneRecord(
            dslContext
                .select(LONG_COUNT)
                .from(
                    dslContext.select(groupFields)
                        .from(table)
                        .where(condition)
                        .groupBy(groupFields)
                )
        )?.value1() ?: 0
    }

    protected suspend fun <R : Record> atMostOneRecord(select: Select<R>): R? {
        return allRecords(select)
            .getSingleOrNull({ it }) {
                val type = select.recordType
                JooqQueryException(select, it, "Found more than one record. Type: $type")
            }
    }

    protected suspend fun <R : Record> allRecords(select: Select<R>): List<R> {
        return queryExecutor.executeSelect(
            dslContext = dslContext,
            jooqQuery = select,
            table = select.asTable()
        )
    }

    @Throws(JooqQueryException::class)
    private fun <T, K> List<T>.getSingleOrNull(mapper: (T) -> K, ex: (List<T>) -> JooqQueryException): K? {
        return when (size) {
            0 -> null
            1 -> mapper(first())
            else -> throw ex(this)
        }
    }

    private inline fun <R : Record, ME : M> wrapException(model: ME, block: () -> List<R>) = try {
        block()
    } catch (ex: DataAccessException) {
        when {
            ex.sqlState() == PgHelpers.PG_UNIQUE_VIOLATION -> {
                val constraintName = PgHelpers.extractUniqueConstraintName(queryExecutor, table, ex)
                val uex = PersistenceException.UniqueModelRecordViolationException(
                    model.id(),
                    table.name,
                    constraintName,
                )
                throw mapConstraintViolation(uex) ?: uex
            }
            ex.sqlStateClass() == SQLStateClass.C23_INTEGRITY_CONSTRAINT_VIOLATION -> {
                val constraintName = PgHelpers.extractConstraintName(queryExecutor, ex)
                val cex = PersistenceException.ModelRecordConstraintViolationException(
                    model.id(),
                    table.name,
                    constraintName,
                )
                throw mapConstraintViolation(cex) ?: cex
            }
            else -> throw PersistenceException.ModelPersistingGenericException(model.id(), ex)
        }
    } catch (ex: PgException) {
        when {
            ex.sqlState == PgHelpers.PG_UNIQUE_VIOLATION -> {
                val uex = PersistenceException.UniqueModelRecordViolationException(
                    model.id(),
                    table.name,
                    ex.constraint,
                )
                throw mapConstraintViolation(uex) ?: uex
            }
            SQLStateClass.fromCode(ex.sqlState) == SQLStateClass.C23_INTEGRITY_CONSTRAINT_VIOLATION -> {
                val cex = PersistenceException.ModelRecordConstraintViolationException(
                    model.id(),
                    table.name,
                    ex.constraint,
                )
                throw mapConstraintViolation(cex) ?: cex
            }
            else -> throw PersistenceException.ModelPersistingGenericException(model.id(), ex)
        }
    }

    protected open fun partitionCond(model: M): Condition = DSL.noCondition()

    protected open fun mapConstraintViolation(ex: PersistenceException.ConstraintViolation): Exception? = null

    private companion object {
        private const val MAX_RETURNED_RECORDS = 1000

        private val LONG_COUNT = DSL.field("count(*)", SQLDataType.BIGINT)
    }
}
