package com.razz.eva.repository

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.ModelState.PersistentState
import com.razz.eva.domain.ModelState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Version.Companion.version
import com.razz.eva.persistence.PersistenceException.ConstraintViolation
import com.razz.eva.persistence.PersistenceException.StaleRecordException
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.jooq.record.BaseModelRecord
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.EnumType
import org.jooq.Field
import org.jooq.Record
import org.jooq.Select
import org.jooq.SelectLimitStep
import org.jooq.StoreQuery
import org.jooq.Table
import org.jooq.TableField
import org.jooq.UpdateQuery
import org.jooq.impl.DSL
import java.time.Instant

abstract class AbstractJooqRepository<ID, MID, M, ME, R>(
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
    private val stripNotModifiedFields: Boolean = false,
)
    where ID : Comparable<ID>,
          MID : ModelId<out Comparable<*>>,
          M : Model<MID, ME>,
          ME : ModelEvent<MID>,
          R : BaseModelRecord<ID> {

    protected abstract fun toRecord(model: M): R

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

    protected fun persistentState(record: R): PersistentState<MID, ME> {
        val recordVersion = requireNotNull(record.getVersion()) {
            "Record version must not be null for persisted model"
        }
        val original = record.original().apply { detach() }
        return persistentState(version(recordVersion), original)
    }

    protected fun protoRecord(model: M): R? {
        if (!stripNotModifiedFields) return null
        return model.proto<R>()
    }

    private fun <Q : StoreQuery<R>> prepareQuery(context: TransactionalContext, model: M, storeQuery: Q): Q {
        val record = toRecord(context, model)
        val protoRecord = protoRecord(model)
        if (protoRecord != null) {
            for (i in 0..<record.size()) {
                val origin = protoRecord.getValue(i)
                val changed = record.getValue(i)
                if (origin == changed) {
                    record.reset(i)
                }
            }
        }
        storeQuery.setRecord(record)
        return storeQuery
    }

    private fun <Q : UpdateQuery<R>> prepareUpdate(updateQuery: Q): Q {
        return updateQuery.apply {
            addConditions(
                DSL.field(DSL.name(VALUES_ALIAS, tableId.unqualifiedName)).coerce(tableId.dataType).eq(
                    DSL.field(DSL.name(ORIGIN_ALIAS, tableId.unqualifiedName)).coerce(tableId.dataType),
                ),
                DSL.field(DSL.name(VALUES_ALIAS, version.unqualifiedName)).coerce(version.dataType).eq(
                    DSL.field(DSL.name(ORIGIN_ALIAS, version.unqualifiedName)).coerce(version.dataType).plus(1),
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

    protected suspend fun persistRecord(context: TransactionalContext, model: M): R {
        require(model.isNew()) {
            "Can insert only new model"
        }
        val insertQuery = prepareQuery(context, model, dslContext.insertQuery(table))
        val added = wrapException(model) {
            queryExecutor.executeStore(
                dslContext = dslContext,
                jooqQuery = insertQuery,
                table = table,
            )
        }.singleOrNull()
        return added ?: throw IllegalStateException("Too many rows updated")
    }

    protected suspend fun persistRecords(context: TransactionalContext, models: List<M>): List<R> {
        when {
            models.isEmpty() -> throw IllegalArgumentException("No models provided for insert")
            models.size == 1 -> {
                // just for optimization's sake, this version is capable of handling single record update
                return listOf(persistRecord(context, models.first()))
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
                table = table,
            )
        }
        if (added.size != models.size) {
            throw IllegalStateException(
                "${models.size} models were queried for insert, while ${added.size} rows were inserted",
            )
        }
        return added
    }

    protected suspend fun updateRecord(context: TransactionalContext, model: M): R {
        require(model.isDirty()) {
            "Can update only dirty model"
        }
        val updateQuery = prepareUpdate(model, prepareQuery(context, model, dslContext.updateQuery(table)))
        val updated = wrapException(model) {
            queryExecutor.executeStore(
                dslContext = dslContext,
                jooqQuery = updateQuery,
                table = table,
            )
        }
        return when (updated.size) {
            0 -> throw StaleRecordException(model.id(), table.name)
            1 -> updated.first()
            else -> {
                val type = model::class
                throw JooqQueryException(updateQuery, updated, "Too many rows updated. Type: $type")
            }
        }
    }

    protected suspend fun updateRecords(context: TransactionalContext, models: List<M>): List<R> {
        when {
            models.isEmpty() -> throw IllegalArgumentException("No models provided for update")
            models.size == 1 -> {
                // just for optimization sake, this version is capable of handling single record update
                return listOf(updateRecord(context, models.first()))
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
                        .map { field ->
                            if (EnumType::class.java.isAssignableFrom(field.type)) {
                                field
                            } else {
                                field.cast(field.dataType)
                            }
                        }
                        .toTypedArray()
                },
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
                table = table,
            )
        }
        when {
            updated.size < models.size -> {
                val modelByDbId = models.associateBy { dbId(it.id()) }
                val updatedDbIds = updated.mapTo(mutableSetOf()) { it.get(tableId)!! }
                val notUpdated = modelByDbId.keys.subtract(updatedDbIds)
                    .mapTo(mutableSetOf()) { modelByDbId.getValue(it).id() }
                throw StaleRecordException(notUpdated, table.name)
            }
            updated.size > models.size -> throw IllegalStateException(
                "Only ${models.size} models were queried for update, while ${updated.size} rows were updated",
            )
        }
        return updated
    }

    private fun Table<*>.onlyModifiableFields(): Array<Field<*>> = this.fields().filterNot { field ->
        field.unqualifiedName == createdAt.unqualifiedName || field.unqualifiedName == tableId.unqualifiedName
    }.toTypedArray()

    private val ORIGIN_ALIAS = DSL.quotedName("T")
    private val ORIGIN_TABLE = table.`as`(ORIGIN_ALIAS)
    private val VALUES_ALIAS = DSL.quotedName("U")
    private val VALUES_ROW = table.fields().filter { createdAt != it }.map { it.unqualifiedName }.toTypedArray()

    private val destinationValues = DSL.row(*table.onlyModifiableFields())
    private val sourceValues = DSL.row(*ORIGIN_TABLE.`as`(VALUES_ALIAS).onlyModifiableFields())

    private inline fun <R : Record, ME : M> wrapException(model: ME, block: () -> List<R>) = try {
        block()
    } catch (ex: Exception) {
        val modelException = queryExecutor.extractModelException(ex, table, model.id()) ?: throw ex
        throw when (modelException) {
            is ConstraintViolation -> mapConstraintViolation(modelException) ?: modelException
            else -> modelException
        }
    }

    protected open fun partitionCond(model: M): Condition = DSL.noCondition()

    protected open fun mapConstraintViolation(ex: ConstraintViolation): Exception? = null

    protected suspend fun <R : Record> atMostOneRecord(select: SelectLimitStep<R>): R? {
        return allRecords(select.limit(2))
            .getSingleOrNull({ it }) {
                val type = select.recordType
                JooqQueryException(select, it, "Found more than one record. Type: $type")
            }
    }

    protected suspend fun <R : Record> allRecords(select: Select<R>): List<R> {
        return queryExecutor.executeSelect(
            dslContext = dslContext,
            jooqQuery = select,
            table = select.asTable(),
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
}
