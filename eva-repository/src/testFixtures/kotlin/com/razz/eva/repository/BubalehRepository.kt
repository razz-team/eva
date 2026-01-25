package com.razz.eva.repository

import com.razz.eva.domain.Bubaleh
import com.razz.eva.domain.Bubaleh.Consumed
import com.razz.eva.domain.Bubaleh.Served
import com.razz.eva.domain.BubalehBottleVol
import com.razz.eva.domain.BubalehEvent
import com.razz.eva.domain.BubalehId
import com.razz.eva.domain.BubalehState
import com.razz.eva.domain.BubalehTaste
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.PersistentState
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.test.schema.Tables.BUBALEHS
import com.razz.eva.test.schema.enums.BubalehsState
import com.razz.eva.test.schema.tables.records.BubalehsRecord
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.util.*

class BubalehRepository(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext
) : JooqStatefulModelRepository<UUID, BubalehId, Bubaleh, BubalehEvent, BubalehsRecord, BubalehsState>(
    queryExecutor = queryExecutor,
    dslContext = dslContext,
    table = BUBALEHS,
    tableId = BUBALEHS.ID,
    dbId = { it.id },
    version = BUBALEHS.VERSION,
    stripNotModifiedFields = true,
) {

    override fun stateOf(model: Bubaleh) = when (model.state()) {
        BubalehState.SERVED -> BubalehsState.SERVED
        BubalehState.CONSUMED -> BubalehsState.CONSUMED
    }

    override fun toRecord(model: Bubaleh): BubalehsRecord =
        BubalehsRecord().apply {
            employeeId = model.employeeId.id
            taste = model.taste.name
            producedOn = model.producedOn
            volume = model.volume.name
        }

    override fun fromRecord(
        record: BubalehsRecord,
        modelState: PersistentState<BubalehId, BubalehEvent>
    ): Bubaleh {
        @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
        return when (record.state) {
            BubalehsState.SERVED -> Served(
                BubalehId(record.id),
                EmployeeId(record.employeeId),
                BubalehTaste.valueOf(record.taste),
                record.producedOn,
                BubalehBottleVol.valueOf(record.volume),
                modelState
            )
            BubalehsState.CONSUMED -> Consumed(
                BubalehId(record.id),
                EmployeeId(record.employeeId),
                BubalehTaste.valueOf(record.taste),
                record.producedOn,
                BubalehBottleVol.valueOf(record.volume),
                modelState
            )
        }
    }

    suspend fun findNBottles(numberOfBottles: Int): List<Served> {
        return findAllWhere(
            condition = BUBALEHS.STATE.eq(BubalehsState.SERVED),
            limit = numberOfBottles
        ).filterIsInstance<Served>()
    }

    suspend fun findAll(): List<Bubaleh> {
        return findAllWhere(DSL.trueCondition())
    }
}
