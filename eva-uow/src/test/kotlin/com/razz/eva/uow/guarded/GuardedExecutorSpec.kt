package com.razz.eva.uow.guarded

import com.razz.eva.domain.Employee
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.persistence.PersistenceException.StaleRecordException
import com.razz.eva.persistence.WithCtxConnectionTransactionManager
import com.razz.eva.repository.EntityRepos
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.ModelRepository
import com.razz.eva.repository.hasRepo
import com.razz.eva.uow.BaseUnitOfWork.Configuration
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.DummyEventRepository
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.Retry.StaleRecordFixedRetry
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UnitOfWorkExecutor
import com.razz.eva.uow.guarded.Fixtures.department
import com.razz.eva.uow.guarded.Fixtures.employee
import com.razz.eva.uow.guarded.Fixtures.proof
import com.razz.eva.uow.guarded.Guarded.Companion.guarded
import com.razz.eva.uow.params.kotlinx.KotlinxParamsSerializer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.opentelemetry.api.OpenTelemetry
import java.time.Duration.ofMillis

/** The kernel under the executor: a retry reloads and decides again, and a failed write is answered on its target. */
class GuardedExecutorSpec : FunSpec({

    val clock = fixedUTC(millisUTC().instant())
    val home = department("Home")
    val away = department("Away")
    val employee = employee(department = home.id())
    val reloaded = employee(id = employee.id(), department = home.id())
    val persisted = employee(id = employee.id(), department = away.id())
    val repo = mockk<ModelRepository<com.razz.eva.domain.EmployeeId, Employee>>(relaxed = true)
    val uowx = UnitOfWorkExecutor(
        listOf(),
        Persisting(
            transactionManager = WithCtxConnectionTransactionManager(),
            modelRepos = ModelRepos(Employee::class hasRepo repo),
            entityRepos = EntityRepos(),
            eventRepository = DummyEventRepository(),
            paramsSerializer = KotlinxParamsSerializer(),
        ),
        clock,
        OpenTelemetry.noop(),
    )
    val retrying = Configuration(retry = StaleRecordFixedRetry(1, ofMillis(0)))

    beforeTest { clearMocks(repo) }

    test("a stale first write retries on the reloaded target, which the proof decides again, and persists that one") {
        val reloads = mutableListOf<Int>()
        coEvery { repo.update(any(), any<Employee>()) }
            .throws(StaleRecordException(setOf(employee.id()), "employee"))
            .andThen(persisted)
        val proof = proof(employee)
        val result = uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away, retrying) }) {
            guarded(proof, MoveEmployeeUow.Params("moves")) { reloads += attempt; reloaded }
        }
        reloads shouldBe listOf(1)
        proof.decidedAgain shouldBe listOf(reloaded)
        result shouldBe persisted
        coVerify(exactly = 2) { repo.update(any(), any<Employee>()) }
    }

    test("a block's roundtrip result is built again over the models the executor persisted") {
        coEvery { repo.update(any(), any<Employee>()) } returns persisted
        val factory = { ctx: ExecutionContext ->
            object : GuardedUnitOfWork<TestPrincipal, TestProof<Employee>, ChildParams, Employee, List<Employee>>(
                ctx, Configuration.default(), EmployeesGuarded, setOf(),
            ) {
                override suspend fun perform(principal: TestPrincipal, params: ChildParams) = changes {
                    val moved = update(target.changeDepartment(away))
                    roundtrip { p -> listOf(p(moved)) }
                }
            }
        }
        val result = uowx.execute(TestPrincipal, factory) {
            guarded(proof(employee), ChildParams) { reloaded }
        }
        result shouldBe listOf(persisted)
    }

    test("a refused reload stops the retry before the body runs") {
        coEvery { repo.update(any(), any<Employee>()) } throws StaleRecordException(setOf(employee.id()), "employee")
        val proof = proof(employee, refused = setOf(home))
        shouldThrow<Refused> {
            uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away, retrying) }) {
                guarded(proof, MoveEmployeeUow.Params("moves")) { reloaded }
            }
        }
        coVerify(exactly = 1) { repo.update(any(), any<Employee>()) }
    }

    test("a write that stays stale is answered by onFailure on the target of the last attempt") {
        coEvery { repo.update(any(), any<Employee>()) } throws StaleRecordException(setOf(employee.id()), "employee")
        val proof = proof(employee)
        val answered = uowx.execute(TestPrincipal, { ctx ->
            object : GuardedUnitOfWork<TestPrincipal, TestProof<Employee>, MoveEmployeeUow.Params, Employee, Employee>(
                ctx,
                retrying,
                EmployeesGuarded,
                setOf(),
            ) {
                override suspend fun perform(principal: TestPrincipal, params: MoveEmployeeUow.Params) = changes {
                    update(target.changeDepartment(away))
                }
                override suspend fun onFailure(
                    target: Employee,
                    params: MoveEmployeeUow.Params,
                    ex: PersistenceException,
                ) = target
            }
        }) { guarded(proof, MoveEmployeeUow.Params("moves")) { reloaded } }
        answered shouldBe reloaded
        proof.decidedAgain shouldBe listOf(reloaded)
    }

    test("one proof runs one unit of work: a second run on it is refused before anything is written") {
        coEvery { repo.update(any(), any<Employee>()) } returns persisted
        val proof = proof(employee)
        uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away) }) {
            guarded(proof, MoveEmployeeUow.Params("first")) { employee }
        }
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away) }) {
                guarded(proof, MoveEmployeeUow.Params("second")) { employee }
            }
        }.message shouldBe "A proof for test.write runs once, and this one ran"
        coVerify(exactly = 1) { repo.update(any(), any<Employee>()) }
    }
})
