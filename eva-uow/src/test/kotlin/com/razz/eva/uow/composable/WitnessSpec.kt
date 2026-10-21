package com.razz.eva.uow.composable

import com.razz.eva.domain.Department.OrphanedDepartment
import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeEvent.DepartmentChanged
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.uow.AddModel
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UpdateModel
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.opentelemetry.api.OpenTelemetry

// The rejections (a witnessed mutator or factory called outside a registration) are compile errors by
// construction; WitnessCompileRejectionSpec pins them in CI. These cover what still has to compile and behave.
class WitnessSpec : FunSpec({

    val executionContext = ExecutionContext(fixedUTC(millisUTC().instant()), OpenTelemetry.noop())
    fun employee(departmentId: com.razz.eva.domain.DepartmentId = randomDepartmentId()) = Employee(
        EmployeeId(), Name("Ada", "Lovelace"), departmentId, "ada@test.com", BUBALEH, persistentState(V1, null),
    )

    test("update(model) { } mints the witness, runs the mutation under it and registers the result") {
        val employee = employee()
        val department = OrphanedDepartment(randomDepartmentId(), "Engineering", 3, BUBALEH, persistentState(V1, null))

        val uow = object : DummyUow<Employee>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(employee) { changeDepartment(department) }
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.result.departmentId shouldBe department.id()
        changes.modelChangesToPersist shouldBe listOf(
            UpdateModel(
                changes.result,
                listOf(DepartmentChanged(employee.id(), employee.departmentId, department.id())),
            ),
        )
    }

    test("update(model) { } accepts a state transition and types the result as the target state") {
        val employee = employee()
        val orphaned = OrphanedDepartment(employee.departmentId, "Engineering", 3, BUBALEH, persistentState(V1, null))

        val uow = object : DummyUow<OwnedDepartment>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(orphaned) { addBoss(employee) }
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.result.boss shouldBe employee.id()
        val change = changes.modelChangesToPersist.single().shouldBeInstanceOf<UpdateModel<*, *, *>>()
        change.model shouldBe changes.result
    }

    test("update(model) { } registers the model as unchanged when the mutation declines and hands it back") {
        val employee = employee()

        val uow = object : DummyUow<Employee>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(employee) { null ?: this }
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.result shouldBe employee
        changes.modelChangesToPersist shouldBe listOf(NoopModel(employee))
    }

    test("update(model) { } registers a receiver dirty from an unwitnessed mutator when the mutation declines") {
        val read = com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel(param1 = "read", param2 = 1)
        val dirty = read.changeParam1("legacy")
        val uow = object : DummyUow<com.razz.eva.domain.TestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(dirty) { this }
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.result shouldBe dirty
        changes.modelChangesToPersist.single().shouldBeInstanceOf<UpdateModel<*, *, *>>().model shouldBe dirty
    }

    test("update(model) { } refuses a mutation that came back as a different model") {
        val employee = employee()
        val other = employee()

        val uow = object : DummyUow<Employee>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(employee) { other }
            }
        }
        val exception = shouldThrow<IllegalStateException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "update(model) { } returned model [${other.id().stringValue()}] instead of " +
            "the mutated [${employee.id().stringValue()}]"
    }

    test("add { } mints the witness a factory needs and registers what it created") {
        val departmentId = randomDepartmentId()

        val uow = object : DummyUow<Employee>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add { newEmployee(Name("Grace", "Hopper"), departmentId, "grace@test.com", BUBALEH) }
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.result.isNew() shouldBe true
        val change = changes.modelChangesToPersist.single().shouldBeInstanceOf<AddModel<*, *, *>>()
        change.model shouldBe changes.result
    }
})
