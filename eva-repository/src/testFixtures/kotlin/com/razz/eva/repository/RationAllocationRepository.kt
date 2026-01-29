package com.razz.eva.repository

import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Ration
import com.razz.eva.domain.RationAllocation
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.test.schema.tables.RationAllocation.RATION_ALLOCATION
import com.razz.eva.test.schema.tables.records.RationAllocationRecord
import org.jooq.Condition
import org.jooq.DSLContext
import java.sql.Date
import java.time.LocalDate

class RationAllocationRepository(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext,
) : JooqBaseEntityRepository<RationAllocation, RationAllocationRecord>(
    queryExecutor,
    dslContext,
    RATION_ALLOCATION,
) {

    override fun toRecord(entity: RationAllocation): RationAllocationRecord =
        RationAllocationRecord(
            entity.employeeId.id,
            entity.ration.name,
            Date.valueOf(entity.effectiveDate),
            entity.quantity,
        )

    override fun fromRecord(record: RationAllocationRecord): RationAllocation =
        RationAllocation(
            EmployeeId(record.employeeId),
            Ration.valueOf(record.ration),
            record.effectiveDate.toLocalDate(),
            record.quantity,
        )

    suspend fun listByEmployee(employeeId: EmployeeId): List<RationAllocation> =
        listAllWhere(RATION_ALLOCATION.EMPLOYEE_ID.eq(employeeId.id))

    suspend fun listByEmployeeAndDate(employeeId: EmployeeId, effectiveDate: LocalDate): List<RationAllocation> =
        listAllWhere(
            RATION_ALLOCATION.EMPLOYEE_ID.eq(employeeId.id)
                .and(RATION_ALLOCATION.EFFECTIVE_DATE.eq(Date.valueOf(effectiveDate))),
        )

    suspend fun existsForEmployee(employeeId: EmployeeId): Boolean =
        existsWhere(RATION_ALLOCATION.EMPLOYEE_ID.eq(employeeId.id))
}
