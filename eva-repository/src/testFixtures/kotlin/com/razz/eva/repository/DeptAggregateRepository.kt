package com.razz.eva.repository

import com.razz.eva.domain.DepartmentEvent
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.DeptAggregate
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelState.PersistentState
import com.razz.eva.domain.Ration
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.test.schema.Tables.DEPARTMENTS
import com.razz.eva.test.schema.enums.DepartmentsState
import com.razz.eva.test.schema.tables.records.DepartmentsRecord
import org.jooq.DSLContext
import java.util.UUID

class DeptAggregateRepository(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext,
    employeeRepo: EmployeeRepository,
) : JooqAggregateRepository<
    UUID,
    DepartmentId,
    DeptAggregate<List<Employee>>,
    DepartmentEvent,
    DepartmentsRecord,
    >(
    queryExecutor = queryExecutor,
    dslContext = dslContext,
    table = DEPARTMENTS,
    stripNotModifiedFields = true,
    ownedModelSpecs = listOf(
        OwnedModelSpec { aggs -> employeeRepo.findByDepartments(aggs.map { it.id() }) },
    ),
) {

    override fun toRecord(
        model: DeptAggregate<List<Employee>>,
    ): DepartmentsRecord = DepartmentsRecord().apply {
        setName(model.name)
        setBoss(model.boss.id)
        setHeadcount(model.headcount)
        setRation(model.ration.name)
        setState(DepartmentsState.OWNED)
    }

    override fun fromRecord(
        record: DepartmentsRecord,
        modelState: PersistentState<DepartmentId, DepartmentEvent>,
        ownedModels: List<Model<*, *>>,
    ): DeptAggregate<List<Employee>> {
        val employees = ownedModels.filterIsInstance<Employee>()
        return DeptAggregate(
            id = DepartmentId(record.id),
            name = record.name,
            boss = EmployeeId(record.boss),
            headcount = record.headcount,
            ration = Ration.valueOf(record.ration),
            employees = employees,
            modelState = modelState,
        )
    }
}
