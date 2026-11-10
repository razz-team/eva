package com.razz.eva.uow.guarded

import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Principal
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
import com.razz.eva.uow.guarded.Proven.Companion.proven
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

/** The kernel under the executor: a retry reloads and decides again, a failed write is answered on what it decided. */
class GuardedExecutorSpec : FunSpec({

    val clock = fixedUTC(millisUTC().instant())
    val home = department("Home")
    val away = department("Away")
    val employee = employee(department = home.id())
    val reloaded = employee(id = employee.id(), department = home.id())
    val persisted = employee(id = employee.id(), department = away.id())
    val repo = mockk<ModelRepository<EmployeeId, Employee>>(relaxed = true)
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

    test("a stale first write retries on the reloaded model, which the proof decides again, and persists that one") {
        val reloads = mutableListOf<Int>()
        coEvery { repo.update(any(), any<Employee>()) }
            .throws(StaleRecordException(setOf(employee.id()), "employee"))
            .andThen(persisted)
        val proof = proof(employee)
        val result = uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away, retrying) }) {
            MoveEmployeeUow.Params(proven(proof) { reloads += attempt; reloaded }, "moves")
        }
        reloads shouldBe listOf(1)
        proof.decidedAgain shouldBe listOf(reloaded)
        result shouldBe persisted
        coVerify(exactly = 2) { repo.update(any(), any<Employee>()) }
    }

    test("a block's roundtrip result is built again over the models the executor persisted") {
        coEvery { repo.update(any(), any<Employee>()) } returns persisted
        val factory = { ctx: ExecutionContext ->
            object : GuardedUnitOfWork<TestPrincipal, TestProof<*>, EmployeeParams, List<Employee>>(
                ctx, Configuration.default(), EmployeesGuarded, setOf(),
            ) {
                override suspend fun tryPerform(principal: TestPrincipal, params: EmployeeParams) = changes {
                    val moved = update(params.employee.model().changeDepartment(away))
                    roundtrip { p -> listOf(p(moved)) }
                }
            }
        }
        val result = uowx.execute(TestPrincipal, factory) { EmployeeParams(proven(proof(employee)) { reloaded }) }
        result shouldBe listOf(persisted)
    }

    test("a refused reload stops the retry before anything more is written") {
        coEvery { repo.update(any(), any<Employee>()) } throws StaleRecordException(setOf(employee.id()), "employee")
        val proof = proof(employee, refused = setOf(home))
        shouldThrow<Refused> {
            uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away, retrying) }) {
                MoveEmployeeUow.Params(proven(proof) { reloaded }, "moves")
            }
        }
        coVerify(exactly = 1) { repo.update(any(), any<Employee>()) }
    }

    test("a write that stays stale is answered by onFailure on the model the last attempt decided") {
        coEvery { repo.update(any(), any<Employee>()) } throws StaleRecordException(setOf(employee.id()), "employee")
        val proof = proof(employee)
        val answered = uowx.execute(
            TestPrincipal,
            { MoveEmployeeUow(it, setOf(), away, retrying, answering = true) },
        ) { MoveEmployeeUow.Params(proven(proof) { reloaded }, "moves") }
        answered shouldBe reloaded
        proof.decidedAgain shouldBe listOf(reloaded)
    }

    test("one proof binds one unit of work: a second run on it is refused before anything is written") {
        coEvery { repo.update(any(), any<Employee>()) } returns persisted
        val proof = proof(employee)
        uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away) }) {
            MoveEmployeeUow.Params(proven(proof) { employee }, "first")
        }
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away) }) {
                MoveEmployeeUow.Params(proven(proof) { employee }, "second")
            }
        }.message shouldBe "This proof for test.write was already used to run a unit of work. One proof runs " +
            "one unit of work: get a new proof for another run"
        coVerify(exactly = 1) { repo.update(any(), any<Employee>()) }
    }

    test("a retry binds only a proof its own run spent, and refuses one another committed run spent") {
        val other = employee(department = home.id())
        val committed = proof(other)
        coEvery { repo.update(any(), any<Employee>()) } returns employee(id = other.id(), department = away.id())
        uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away) }) {
            MoveEmployeeUow.Params(proven(committed) { other }, "spends it")
        }
        clearMocks(repo)
        coEvery { repo.update(any(), any<Employee>()) } throws StaleRecordException(setOf(employee.id()), "employee")
        val proof = proof(employee)
        var binds = 0
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away, retrying) }) {
                MoveEmployeeUow.Params(proven(if (binds++ == 0) proof else committed) { other }, "switches")
            }
        }.message shouldBe "The proof for test.write was not bound in the first attempt of this run. Bind the " +
            "same proofs in every attempt. In a spec, build the first attempt's params before the retry's"
        committed.decidedAgain shouldBe listOf()
        coVerify(exactly = 1) { repo.update(any(), any<Employee>()) }
    }

    test("a proof minted for another principal than the run's is refused as the params bind it") {
        val proof = TestProof(OtherPrincipal, employee) { }
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away) }) {
                MoveEmployeeUow.Params(proven(proof) { employee }, "moves")
            }
        }.message shouldBe "The proof for test.write on ${employee.id().stringValue()} was issued for another " +
            "principal than the one this unit of work runs under. Run it under the proof's principal"
        coVerify(exactly = 0) { repo.update(any(), any<Employee>()) }
    }

    test("a retry handed the first attempt's Proven is refused before it writes again, and reloads nothing") {
        coEvery { repo.update(any(), any<Employee>()) } throws StaleRecordException(setOf(employee.id()), "employee")
        val proof = proof(employee)
        var bound: Proven<TestProof<Employee>, Employee>? = null
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away, retrying) }) {
                MoveEmployeeUow.Params(bound ?: proven(proof) { reloaded }.also { bound = it }, "moves")
            }
        }.message shouldBe "MoveEmployeeUow: another block already read the Proven for " +
            "${employee.id().stringValue()}. Build the params in the params lambda for each attempt, and do not keep " +
            "a Proven between attempts or runs"
        proof.decidedAgain shouldBe listOf()
        coVerify(exactly = 1) { repo.update(any(), any<Employee>()) }
    }

    test("a Proven leaked from a run whose params lambda threw is refused in another run, before anything is written") {
        var leaked: Proven<TestProof<Employee>, Employee>? = null
        shouldThrow<Refused> {
            uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away) }) {
                leaked = proven(proof(employee)) { reloaded }
                throw Refused("the params lambda fails after the bind")
            }
        }
        val anyone = { ctx: ExecutionContext ->
            object : GuardedUnitOfWork<Principal<*>, TestProof<*>, EmployeeParams, Employee>(
                ctx, Configuration.default(), EmployeesGuarded, setOf(),
            ) {
                override suspend fun tryPerform(principal: Principal<*>, params: EmployeeParams) =
                    changes { update(params.employee.model().changeDepartment(away)) }
            }
        }
        shouldThrow<IllegalStateException> {
            uowx.execute(OtherPrincipal, anyone) { EmployeeParams(checkNotNull(leaked)) }
        }.message shouldBe "A guarded unit of work cannot read the Proven for ${employee.id().stringValue()}: " +
            "its params were built in another run, or for another principal. Build them in the params lambda of the " +
            "execute call that runs this unit of work. In a spec, call tryPerform inside inAttempt(context), with " +
            "the context that built the params"
        coVerify(exactly = 0) { repo.update(any(), any<Employee>()) }
    }
})
