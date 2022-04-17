package com.razz.eva.repository

import com.razz.eva.domain.EggsCount
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.EntityState.PersistentState
import com.razz.eva.domain.Shakshouka
import com.razz.eva.domain.ShakshoukaEvent
import com.razz.eva.domain.ShakshoukaId
import com.razz.eva.domain.ShakshoukaState
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.test.schema.Tables.SHAKSHOUKAS
import com.razz.eva.test.schema.enums.ShakshoukasState
import com.razz.eva.test.schema.enums.ShakshoukasState.CONSUMED
import com.razz.eva.test.schema.enums.ShakshoukasState.SERVED
import com.razz.eva.test.schema.tables.records.ShakshoukasRecord
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.util.*

class ShakshoukaRepository(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext,
    preUpdate: PreModifyCallback<UUID, ShakshoukaId, Shakshouka> = PreModifyCallback { }
) : HackedRepository<
    UUID, ShakshoukaId, Shakshouka, ShakshoukaEvent, ShakshoukasRecord, ShakshoukasState
    >(
    queryExecutor,
    dslContext,
    SHAKSHOUKAS,
    preUpdate
) {

    override fun stateOf(model: Shakshouka) = when (model.state()) {
        ShakshoukaState.SERVED -> SERVED
        ShakshoukaState.CONSUMED -> CONSUMED
    }

    override fun toRecord(model: Shakshouka): ShakshoukasRecord =
        ShakshoukasRecord().apply {
            employeeId = model.employeeId.id
            eggsCount = model.eggsCount.name
            withPita = model.withPita
        }

    override fun fromRecord(
        record: ShakshoukasRecord,
        entityState: PersistentState<ShakshoukaId, ShakshoukaEvent>
    ): Shakshouka {
        @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
        return when (record.state) {
            SERVED ->
                Shakshouka.Served(
                    ShakshoukaId(record.id),
                    EmployeeId(record.employeeId),
                    EggsCount.valueOf(record.eggsCount),
                    record.withPita,
                    entityState
                )
            CONSUMED ->
                Shakshouka.Consumed(
                    ShakshoukaId(record.id),
                    EmployeeId(record.employeeId),
                    EggsCount.valueOf(record.eggsCount),
                    record.withPita,
                    entityState
                )
        }
    }

    suspend fun findNCookedPortions(numberOfPortions: Int): List<Shakshouka.Served> {
        return findAllWhere(
            condition = SHAKSHOUKAS.STATE.eq(SERVED),
            limit = numberOfPortions
        ).filterIsInstance<Shakshouka.Served>()
    }

    suspend fun findAll(): List<Shakshouka> {
        return findAllWhere(DSL.trueCondition())
    }
}
