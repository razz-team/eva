package com.razz.eva.uow.func

import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.DeptAggregate
import io.kotest.matchers.string.shouldContain
import io.kotest.assertions.throwables.shouldThrow
import com.razz.eva.uow.composable.ChangesDsl
import com.razz.eva.persistence.PersistenceException.ModelRecordConstraintViolationException
import com.razz.eva.domain.addEmployee
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.NewState.Companion.newState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.repository.DeptAggregateRepository
import com.razz.eva.repository.EntityRepos
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.hasRepo
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UnitOfWorkExecutor
import com.razz.eva.uow.composable.DummyUow
import com.razz.eva.uow.params.kotlinx.KotlinxParamsSerializer
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.OpenTelemetry
import java.util.UUID.randomUUID
import kotlin.reflect.KClass

// The aggregate route the README gives for witnessed children: each child goes through its own add { },
// so it is registered, and persisted, before the root that owns it. Against the foreign key from employees
// to departments, which this schema does not defer, that holds for a root that already exists and fails
// for a new one: the documented limit.
class WitnessedAggregateSpec : PersistenceBaseSpec({

    Given("Department aggregates whose employee is created by a witnessed factory") {
        val aggregateRepo = DeptAggregateRepository(module.queryExecutor, module.dslContext, module.employeeRepo)
        @Suppress("UNCHECKED_CAST")
        val aggregateClass = DeptAggregate::class as KClass<DeptAggregate<List<Employee>>>
        val uowx = UnitOfWorkExecutor(
            factories = listOf(),
            persisting = Persisting(
                transactionManager = module.transactionManager,
                modelRepos = ModelRepos(
                    aggregateClass hasRepo aggregateRepo,
                    Employee::class hasRepo module.employeeRepo,
                ),
                entityRepos = EntityRepos(),
                eventRepository = module.eventRepository,
                paramsSerializer = KotlinxParamsSerializer(),
            ),
            clock = module.clock,
            openTelemetry = OpenTelemetry.noop(),
        )
        val bossId = EmployeeId()
        fun <R : Any> uow(block: suspend ChangesDsl.() -> R) = { executionContext: ExecutionContext ->
            object : DummyUow<R>(executionContext) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes(block)
            }
        }
        fun newDept(departmentId: DepartmentId, name: String, employees: List<Employee>) = DeptAggregate(
            departmentId, name, bossId, 1, BUBALEH, employees,
            newState(OwnedDepartmentCreated(departmentId, name, bossId, 1, BUBALEH)),
        )

        When("A UoW adds the employee, then updates the existing department to own it") {
            val departmentId = randomDepartmentId()
            val tag = randomUUID().toString().take(8)
            uowx.execute(TestPrincipal, uow { add(newDept(departmentId, "dept-$tag", listOf())) }) { DummyUow.Params }
            val existing = checkNotNull(aggregateRepo.find(departmentId))
            uowx.execute(
                TestPrincipal,
                uow {
                    val hire = add { newEmployee(Name("Hire", tag), departmentId, "$tag@razz.team", BUBALEH) }
                    update(existing.addEmployee(hire))
                },
            ) { DummyUow.Params }

            Then("Both persist") {
                val persisted = checkNotNull(aggregateRepo.find(departmentId))
                persisted.employees.map { it.name } shouldBe listOf(Name("Hire", tag))
            }
        }

        When("A UoW adds the employee, then a new department that owns it") {
            val departmentId = randomDepartmentId()
            val tag = randomUUID().toString().take(8)
            val attempt = suspend {
                uowx.execute(
                    TestPrincipal,
                    uow {
                        val hire = add { newEmployee(Name("Hire", tag), departmentId, "$tag@razz.team", BUBALEH) }
                        add(newDept(departmentId, "dept-$tag", listOf(hire)))
                    },
                ) { DummyUow.Params }
            }

            Then("The employee is inserted first and the foreign key refuses it; nothing persists") {
                val ex = shouldThrow<ModelRecordConstraintViolationException> { attempt() }
                checkNotNull(ex.message) shouldContain "employees_department_id_fkey"
                aggregateRepo.find(departmentId) shouldBe null
            }
        }
    }
})
