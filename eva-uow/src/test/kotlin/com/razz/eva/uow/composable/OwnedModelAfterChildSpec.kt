package com.razz.eva.uow.composable

import com.razz.eva.domain.Department.Companion.newDepartment
import com.razz.eva.domain.DepartmentEvent
import com.razz.eva.domain.DeptAggregate
import com.razz.eva.domain.DeptAggregate.Companion.newDeptAggregate
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.mutating
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.OpenTelemetry

class OwnedModelAfterChildSpec : FunSpec({

    val executionContext = ExecutionContext(fixedUTC(millisUTC().instant()), OpenTelemetry.noop())

    test("A parent that changes an owned model after its child registered the aggregate persists the newer one") {
        val bossId = EmployeeId()
        val dept = newDeptAggregate("Engineering", bossId, BUBALEH)
        val employee = mutating { newEmployee(Name("Ann", "Lee"), dept.id(), "ann@test.com", BUBALEH) }
        val deptWithEmployee: DeptAggregate<List<Employee>> = DeptAggregate(
            dept.id(), dept.name, dept.boss, dept.headcount, dept.ration, listOf(employee),
            dept.raise(DepartmentEvent.NameChanged(dept.id(), dept.name, "Eng")),
        )
        val moved = mutating { employee.changeDepartment(newDepartment("Other", bossId, 1, BUBALEH)) }
        val deptWithMoved: DeptAggregate<List<Employee>> = DeptAggregate(
            dept.id(), dept.name, dept.boss, dept.headcount, dept.ration, listOf(moved),
            deptWithEmployee.raise(DepartmentEvent.NameChanged(dept.id(), "Eng", "Eng v2")),
        )
        val innerUow = { ctx: ExecutionContext ->
            object : DummyUow<String>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                    add(deptWithEmployee)
                    "child"
                }
            }
        }
        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                execute(innerUow, TestPrincipal) { Params }
                update(deptWithMoved)
                "parent"
            }
        }

        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.modelChangesToPersist.map { it.model } shouldBe listOf(deptWithMoved, moved)
        changes.modelChangesToPersist.map { it.modelEvents.size } shouldBe listOf(3, 2)
    }
})
