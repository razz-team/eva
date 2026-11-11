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
import com.razz.eva.uow.ExecutionContextApi
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.Retry.StaleRecordFixedRetry
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UnitOfWorkExecutor
import com.razz.eva.uow.composable.UnitOfWork
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

/** A guarded unit of work run by the executor: a retry reloads its model, and onFailure gets the model last read. */
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
    fun builtElsewhere(uow: String) = "$uow was built with an ExecutionContext other than the one its factory " +
        "received. Build the unit of work in the factory, with the ExecutionContext the factory receives"
    fun plain(context: ExecutionContext) = object : UnitOfWork<TestPrincipal, ChildParams, String>(context) {
        override fun name() = "Plain"
        override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) = noChanges("plain")
    }

    test("a stale first write retries with the reloaded model, which the proof allows again, and persists that model") {
        val reloads = mutableListOf<Int>()
        coEvery { repo.update(any(), any<Employee>()) }
            .throws(StaleRecordException(setOf(employee.id()), "employee"))
            .andThen(persisted)
        val proof = proof(employee)
        val result = uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away, retrying) }) {
            MoveEmployeeUow.Params(proven(proof) { reloads += attempt; reloaded }, "moves")
        }
        reloads shouldBe listOf(1)
        proof.checkedAgain shouldBe listOf(reloaded)
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
                    val moved = update(params.employee.model()) { changeDepartment(away) }
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

    test("onFailure gets the model the last attempt read when the write stays stale") {
        coEvery { repo.update(any(), any<Employee>()) } throws StaleRecordException(setOf(employee.id()), "employee")
        val proof = proof(employee)
        val answered = uowx.execute(
            TestPrincipal,
            { MoveEmployeeUow(it, setOf(), away, retrying, answering = true) },
        ) { MoveEmployeeUow.Params(proven(proof) { reloaded }, "moves") }
        answered shouldBe reloaded
        proof.checkedAgain shouldBe listOf(reloaded)
    }

    test("one proof runs one unit of work: a second run with it is refused before anything is written") {
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

    test("a retry accepts only a proof the first attempt of its run used, and refuses one another run used") {
        val other = employee(department = home.id())
        val committed = proof(other)
        coEvery { repo.update(any(), any<Employee>()) } returns employee(id = other.id(), department = away.id())
        uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away) }) {
            MoveEmployeeUow.Params(proven(committed) { other }, "uses it")
        }
        clearMocks(repo)
        coEvery { repo.update(any(), any<Employee>()) } throws StaleRecordException(setOf(employee.id()), "employee")
        val proof = proof(employee)
        var binds = 0
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away, retrying) }) {
                MoveEmployeeUow.Params(proven(if (binds++ == 0) proof else committed) { other }, "switches")
            }
        }.message shouldBe "The proof for test.write was not wrapped with proven(...) in the first attempt of " +
            "this run. Wrap the same proofs in every attempt. In a spec, build the first attempt's params before " +
            "the retry's"
        committed.checkedAgain shouldBe listOf()
        coVerify(exactly = 1) { repo.update(any(), any<Employee>()) }
    }

    test("a proof issued for another principal than the run's is refused when the params wrap it") {
        val proof = TestProof(OtherPrincipal, employee) { }
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away) }) {
                MoveEmployeeUow.Params(proven(proof) { employee }, "moves")
            }
        }.message shouldBe "The proof for test.write on ${employee.id().stringValue()} was issued for another " +
            "principal than the one this unit of work runs under. Run it under the proof's principal"
        coVerify(exactly = 0) { repo.update(any(), any<Employee>()) }
    }

    test("a retry given the first attempt's Proven is refused before it writes again, and reloads nothing") {
        coEvery { repo.update(any(), any<Employee>()) } throws StaleRecordException(setOf(employee.id()), "employee")
        val proof = proof(employee)
        var bound: Proven<TestProof<Employee>, Employee>? = null
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away, retrying) }) {
                MoveEmployeeUow.Params(bound ?: proven(proof) { reloaded }.also { bound = it }, "moves")
            }
        }.message shouldBe "MoveEmployeeUow cannot read the Proven for ${employee.id().stringValue()}: its params " +
            "were built in another attempt or another run. Build them in the params lambda of the execute call that " +
            "runs this unit of work. In a spec, build the unit of work with executionContext.forAttempt(context), " +
            "with the context that built the params"
        proof.checkedAgain shouldBe listOf()
        coVerify(exactly = 1) { repo.update(any(), any<Employee>()) }
    }

    test("a Proven leaked from a run whose params lambda threw is refused in another run, before anything is written") {
        var leaked: Proven<TestProof<Employee>, Employee>? = null
        shouldThrow<Refused> {
            uowx.execute(TestPrincipal, { MoveEmployeeUow(it, setOf(), away) }) {
                leaked = proven(proof(employee)) { reloaded }
                throw Refused("the params lambda fails after it wraps the proof")
            }
        }
        val anyone = { ctx: ExecutionContext ->
            object : GuardedUnitOfWork<Principal<*>, TestProof<*>, EmployeeParams, Employee>(
                ctx, Configuration.default(), EmployeesGuarded, setOf(),
            ) {
                override suspend fun tryPerform(principal: Principal<*>, params: EmployeeParams) =
                    changes { update(params.employee.model()) { changeDepartment(away) } }
            }
        }
        shouldThrow<IllegalStateException> {
            uowx.execute(OtherPrincipal, anyone) { EmployeeParams(checkNotNull(leaked)) }
        }.message shouldBe "A guarded unit of work cannot read the Proven for ${employee.id().stringValue()}: " +
            "its params were built in another attempt or another run. Build them in the params lambda of the " +
            "execute call that runs this unit of work. In a spec, build the unit of work with " +
            "executionContext.forAttempt(context), with the context that built the params"
        coVerify(exactly = 0) { repo.update(any(), any<Employee>()) }
    }

    test("a retry whose factory builds the unit of work with the first attempt's context is refused before it reads") {
        coEvery { repo.update(any(), any<Employee>()) } throws StaleRecordException(setOf(employee.id()), "employee")
        val proof = proof(employee)
        var kept: ExecutionContext? = null
        var reloads = 0
        val keeping = { ctx: ExecutionContext ->
            MoveEmployeeUow(kept ?: ctx.also { kept = it }, setOf(), away, retrying)
        }
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, keeping) {
                MoveEmployeeUow.Params(proven(proof) { reloads++; reloaded }, "moves")
            }
        }.message shouldBe builtElsewhere("MoveEmployeeUow")
        reloads shouldBe 0
        proof.checkedAgain shouldBe listOf()
        coVerify(exactly = 1) { repo.update(any(), any<Employee>()) }
    }

    test("a factory that builds the unit of work with a context of another run is refused before the params lambda") {
        coEvery { repo.update(any(), any<Employee>()) } returns persisted
        var kept: ExecutionContext? = null
        val keeping = { ctx: ExecutionContext -> MoveEmployeeUow(ctx.also { kept = it }, setOf(), away) }
        uowx.execute(TestPrincipal, keeping) {
            MoveEmployeeUow.Params(proven(proof(employee)) { reloaded }, "first")
        }
        var built = 0
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, { _: ExecutionContext -> MoveEmployeeUow(checkNotNull(kept), setOf(), away) }) {
                built++
                MoveEmployeeUow.Params(proven(proof(employee)) { reloaded }, "second")
            }
        }.message shouldBe builtElsewhere("MoveEmployeeUow")
        @OptIn(ExecutionContextApi::class)
        val detached = uowx.executionContext()
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, { _: ExecutionContext -> plain(detached) }) {
                built++
                ChildParams
            }
        }.message shouldBe builtElsewhere("Plain")
        built shouldBe 0
        coVerify(exactly = 1) { repo.update(any(), any<Employee>()) }
    }
})
