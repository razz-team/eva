package com.razz.eva.repository

import com.razz.eva.domain.Aggregate
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.ModelState.PersistentState
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.jooq.record.BaseModelRecord
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Table
import org.jooq.TableField
import org.jooq.impl.DSL
import java.time.Instant

fun interface OwnedModelSpec<MID : ModelId<out Comparable<*>>, A : Aggregate<MID, *>> {
    suspend fun loadForParents(parents: List<A>): Map<MID, List<Model<*, *>>>
}

abstract class JooqAggregateRepository<ID, MID, A, AE, R>(
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
    private val ownedModelSpecs: List<OwnedModelSpec<MID, A>> = listOf(),
) : AbstractJooqRepository<ID, MID, A, AE, R>(
    queryExecutor, dslContext, table, tableId, dbId, version, createdAt, stripNotModifiedFields,
), ModelRepository<MID, A>
    where ID : Comparable<ID>,
          MID : ModelId<out Comparable<*>>,
          A : Aggregate<MID, AE>,
          AE : ModelEvent<MID>,
          R : BaseModelRecord<ID> {

    protected abstract fun fromRecord(
        record: R,
        modelState: PersistentState<MID, AE>,
        ownedModels: List<Model<*, *>>,
    ): A

    override suspend fun <ME : A> add(context: TransactionalContext, model: ME): ME {
        val record = persistRecord(context, model)
        @Suppress("UNCHECKED_CAST")
        return fromRecord(record, persistentState(record), model.ownedModels()) as ME
    }

    override suspend fun <ME : A> add(context: TransactionalContext, models: List<ME>): List<ME> {
        val records = persistRecords(context, models)
        return records.mapIndexed { idx, record ->
            @Suppress("UNCHECKED_CAST")
            fromRecord(record, persistentState(record), models[idx].ownedModels()) as ME
        }
    }

    override suspend fun <ME : A> update(context: TransactionalContext, model: ME): ME {
        val record = updateRecord(context, model)
        @Suppress("UNCHECKED_CAST")
        return fromRecord(record, persistentState(record), model.ownedModels()) as ME
    }

    override suspend fun <ME : A> update(context: TransactionalContext, models: List<ME>): List<ME> {
        val records = updateRecords(context, models)
        val modelByDbId = models.associateBy { dbId(it.id()) }
        return records.map { record ->
            val model = modelByDbId.getValue(record.get(tableId)!!)
            @Suppress("UNCHECKED_CAST")
            fromRecord(record, persistentState(record), model.ownedModels()) as ME
        }
    }

    override suspend fun find(id: MID): A? {
        val record = atMostOneRecord(dslContext.selectFrom(table).where(tableId.eq(dbId(id)))) ?: return null
        return gather(listOf(record)).single()
    }

    override suspend fun list(ids: Collection<MID>): List<A> {
        val uniqueIds = ids.toSet()
        if (uniqueIds.isEmpty()) return listOf()
        val uniqueDbIds = uniqueIds.map { dbId(it) }
        val condition = when {
            uniqueDbIds.size <= 3 -> tableId.`in`(uniqueDbIds)
            else -> {
                val idParams = uniqueDbIds.map<ID, Field<ID>>(DSL::`val`).toTypedArray()
                tableId.eq(DSL.any(*idParams))
            }
        }
        val records = allRecords(dslContext.selectFrom(table).where(condition))
        if (records.isEmpty()) return listOf()
        return gather(records)
    }

    private suspend fun gather(records: List<R>): List<A> {
        val states = records.map(::persistentState)
        val rootModels = records.mapIndexed { idx, record -> fromRecord(record, states[idx], listOf()) }
        val ownedByParent = mutableMapOf<MID, MutableList<Model<*, *>>>()
        for (spec in ownedModelSpecs) {
            val byParent = spec.loadForParents(rootModels)
            for ((parentId, models) in byParent) {
                ownedByParent.getOrPut(parentId) { mutableListOf() }.addAll(models)
            }
        }
        return records.mapIndexed { idx, record ->
            val parentId = rootModels[idx].id()
            fromRecord(record, states[idx], ownedByParent[parentId] ?: listOf())
        }
    }
}
