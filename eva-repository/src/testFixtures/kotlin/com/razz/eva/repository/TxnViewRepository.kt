package com.razz.eva.repository

import com.razz.eva.domain.TxnView
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.test.schema.tables.TxnView as TxnViewTable
import com.razz.eva.test.schema.tables.records.TxnViewRecord
import org.jooq.Condition
import org.jooq.DSLContext

class TxnViewRepository(
    private val qe: QueryExecutor,
    private val dsl: DSLContext,
) : JooqKeyUpdatableEntityRepository<TxnView, TxnView.Key, TxnViewRecord>(qe, dsl, TxnViewTable.TXN_VIEW) {

    override fun toRecord(entity: TxnView): TxnViewRecord =
        TxnViewRecord(entity.transactionId, entity.value, entity.currency)

    override fun fromRecord(record: TxnViewRecord): TxnView =
        TxnView(record.transactionId, record.value, record.currency)

    override fun entityCondition(entity: TxnView): Condition =
        TxnViewTable.TXN_VIEW.TRANSACTION_ID.eq(entity.transactionId)

    override fun keyCondition(key: TxnView.Key): Condition =
        TxnViewTable.TXN_VIEW.TRANSACTION_ID.eq(key.transactionId)
}
