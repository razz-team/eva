package com.razz.eva.domain

import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.ModelState.NewState.Companion.newState
import java.util.Objects
import java.util.UUID.randomUUID

/**
 * Test aggregate model that maps to the same `departments` table as [Department],
 * but carries child models via a nullable-bounded type parameter.
 *
 * - `E : List<Employee>?` -- collection model child (employees in this department)
 *
 * `DeptAggregate<List<Employee>>` - loaded aggregate (children accessible)
 * `DeptAggregate<Nothing?>` - root-only (children are null, nullable typing prevents access)
 */
class DeptAggregate<E : List<Employee>?>(
    id: DepartmentId,
    val name: String,
    val boss: EmployeeId,
    val headcount: Int,
    val ration: Ration,
    val employees: E,
    modelState: ModelState<DepartmentId, DepartmentEvent>,
) : Aggregate<DepartmentId, DepartmentEvent>(id, modelState, listOfNotNull(employees).flatten()) {

    fun raise(event: DepartmentEvent) = raiseEvent(event)

    fun rename(newName: String): DeptAggregate<E> {
        check(newName != name) { "Same name" }
        return DeptAggregate(
            id(), newName, boss, headcount, ration, employees,
            raise(DepartmentEvent.NameChanged(id(), name, newName)),
        )
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is DeptAggregate<*> ->
                id() == other.id() &&
                    name == other.name &&
                    boss == other.boss &&
                    headcount == other.headcount &&
                    ration == other.ration
            else -> false
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(id(), name, boss, headcount, ration)
    }

    companion object {
        fun newDeptAggregate(
            name: String,
            boss: EmployeeId,
            ration: Ration,
            employees: List<Employee> = listOf(),
        ): DeptAggregate<List<Employee>> {
            val depId = DepartmentId(randomUUID())
            return DeptAggregate(
                id = depId,
                name = name,
                boss = boss,
                headcount = 1,
                ration = ration,
                employees = employees,
                modelState = newState(
                    OwnedDepartmentCreated(depId, name, boss, 1, ration),
                ),
            )
        }
    }
}

fun DeptAggregate<List<Employee>>.addEmployee(
    employee: Employee,
): DeptAggregate<List<Employee>> {
    check(employee.ration == ration) { "Ration should match" }
    return DeptAggregate(
        id(), name, boss, headcount.inc(), ration, employees + employee,
        raise(DepartmentEvent.EmployeeAdded(id(), employee.id(), headcount.inc())),
    )
}
