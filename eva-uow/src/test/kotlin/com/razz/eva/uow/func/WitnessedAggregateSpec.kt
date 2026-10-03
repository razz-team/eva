package com.razz.eva.uow.func

import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.DeptAggregate
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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.OpenTelemetry
import java.util.UUID.randomUUID
import kotlin.reflect.KClass

// The aggregate route the README gives for witnessed children: each child goes through its own add { },
// so it is registered before the root that owns it, yet must persist after it for the foreign key from
// employees to departments, which this schema does not defer.
class WitnessedAggregateSpec : PersistenceBaseSpec({

    Given("A department aggregate whose employee is created by a witnessed factory") {
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
        val departmentId = randomDepartmentId()
        val bossId = EmployeeId()
        val tag = randomUUID().toString().take(8)
        val createBoth = { executionContext: ExecutionContext ->
            object : DummyUow<DeptAggregate<List<Employee>>>(executionContext) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                    val hire = add { newEmployee(Name("Hire", tag), departmentId, "$tag@razz.team", BUBALEH) }
                    add(
                        DeptAggregate(
                            departmentId, "dept-$tag", bossId, 1, BUBALEH, listOf(hire),
                            newState(OwnedDepartmentCreated(departmentId, "dept-$tag", bossId, 1, BUBALEH)),
                        ),
                    )
                }
            }
        }

        When("A UoW adds the employee, then the department that owns it") {
            uowx.execute(TestPrincipal, createBoth) { DummyUow.Params }

            Then("Both persist: the department is inserted before the employee that references it") {
                val persisted = checkNotNull(aggregateRepo.find(departmentId))
                persisted.employees shouldHaveSize 1
                persisted.employees.single().name shouldBe Name("Hire", tag)
            }
        }
    }
})
