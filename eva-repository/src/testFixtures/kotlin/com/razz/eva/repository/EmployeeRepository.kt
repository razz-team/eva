package com.razz.eva.repository

import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeEvent
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.EntityState.PersistentState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.test.schema.Tables.EMPLOYEES
import com.razz.eva.test.schema.tables.records.EmployeesRecord
import org.jooq.DSLContext
import java.util.*

class EmployeeRepository(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext
) : JooqBaseModelRepository<UUID, EmployeeId, Employee, EmployeeEvent, EmployeesRecord>(
    queryExecutor,
    dslContext,
    EMPLOYEES
) {

    override fun toRecord(model: Employee): EmployeesRecord =
        EmployeesRecord().apply {
            firstName = model.name.first
            lastName = model.name.last
            departmentId = model.departmentId.id
            email = model.email
            ration = model.ration.name
        }

    override fun fromRecord(
        record: EmployeesRecord,
        entityState: PersistentState<EmployeeId, EmployeeEvent>
    ): Employee {
        return Employee(
            EmployeeId(record.id),
            Name(record.firstName, record.lastName),
            DepartmentId(record.departmentId),
            record.email,
            Ration.valueOf(record.ration),
            entityState
        )
    }

    suspend fun findByDepartment(departmentId: DepartmentId): List<Employee> {
        return findAllWhere(EMPLOYEES.DEPARTMENT_ID.eq(departmentId.id))
    }

    suspend fun findByName(name: Name): Employee? {
        return findOneWhere(EMPLOYEES.FIRST_NAME.eq(name.first).and(EMPLOYEES.LAST_NAME.eq(name.last)))
    }
}
