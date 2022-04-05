package com.razz.eva.repository

import com.razz.eva.domain.Department
import com.razz.eva.domain.Department.OrphanedDepartment
import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.EntityState.PersistentState
import com.razz.eva.domain.Ration
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.test.db.Tables.DEPARTMENTS
import com.razz.eva.test.db.enums.DepartmentsState
import com.razz.eva.test.db.enums.DepartmentsState.ORPHANED
import com.razz.eva.test.db.enums.DepartmentsState.OWNED
import com.razz.eva.test.db.tables.records.DepartmentsRecord
import org.jooq.DSLContext
import java.util.*

class DepartmentRepository(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext,
    preUpdate: PreModifyCallback<UUID, DepartmentId, Department<*>> = PreModifyCallback { }
) : HackedRepository<
    UUID, DepartmentId, Department<*>, DepartmentEvent, DepartmentsRecord, DepartmentsState
    >(
    queryExecutor,
    dslContext,
    DEPARTMENTS,
    preUpdate
) {

    override fun stateOf(model: Department<*>): DepartmentsState {
        return when (model) {
            is OwnedDepartment -> OWNED
            is OrphanedDepartment -> ORPHANED
        }
    }

    override fun toRecord(model: Department<*>): DepartmentsRecord =
        DepartmentsRecord().apply {
            name = model.name
            boss = model.boss?.id
            headcount = model.headcount
            ration = model.ration.name
        }

    override fun fromRecord(
        record: DepartmentsRecord,
        entityState: PersistentState<DepartmentId, DepartmentEvent>
    ): Department<*> {
        when (record.boss) {
            null -> return OrphanedDepartment(
                DepartmentId(record.id),
                record.name,
                record.headcount,
                Ration.valueOf(record.ration),
                entityState
            )
            else -> return OwnedDepartment(
                DepartmentId(record.id),
                record.name,
                EmployeeId(record.boss),
                record.headcount,
                Ration.valueOf(record.ration),
                entityState
            )
        }
    }

    suspend fun existsFor(boss: EmployeeId): Boolean {
        return existsWhere(
            DEPARTMENTS.BOSS.eq(boss.id)
        )
    }

    suspend fun findByBoss(boss: EmployeeId): OwnedDepartment? {
        return findOneWhere(DEPARTMENTS.BOSS.eq(boss.id)) as OwnedDepartment?
    }

    suspend fun findByName(departmentName: String): Department<*>? {
        return findOneWhere(DEPARTMENTS.NAME.eq(departmentName))
    }
}
