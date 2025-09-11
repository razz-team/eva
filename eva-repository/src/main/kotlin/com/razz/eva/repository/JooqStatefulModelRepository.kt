package com.razz.eva.repository

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.jooq.record.TypedStatefulEntityRecord
import org.jooq.DSLContext
import org.jooq.Table
import org.jooq.TableField
import java.time.Instant

abstract class JooqStatefulModelRepository<ID, MID, M, ME, R, S>(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext,
    table: Table<R>,
    @Suppress("UNCHECKED_CAST")
    tableId: TableField<R, ID> = requireNotNull(table.primaryKey).fields.single() as TableField<R, ID>,
    @Suppress("UNCHECKED_CAST")
    dbId: (MID) -> ID = { mid -> mid.id as ID },
    @Suppress("UNCHECKED_CAST")
    version: TableField<R, Long> = table.recordVersion as TableField<R, Long>,
    @Suppress("UNCHECKED_CAST")
    createdAt: TableField<R, Instant> = table.field("record_created_at") as TableField<R, Instant>,
    stripNotModifiedFields: Boolean = false,
) : JooqBaseModelRepository<ID, MID, M, ME, R>(
    queryExecutor, dslContext, table, tableId, dbId, version, createdAt, stripNotModifiedFields,
) where ID : Comparable<ID>,
      MID : ModelId<out Comparable<*>>,
      ME : ModelEvent<MID>,
      M : Model<MID, ME>,
      R : TypedStatefulEntityRecord<ID, S>,
      S : Enum<S> {

    protected abstract fun stateOf(model: M): S

    final override fun modelBaseToRecord(context: TransactionalContext, record: R, model: M): R {
        return super.modelBaseToRecord(context, record, model)
            .apply { setState(stateOf(model)) }
    }
}
