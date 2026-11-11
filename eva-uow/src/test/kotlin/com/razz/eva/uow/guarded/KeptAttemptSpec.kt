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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.time.Duration.ofMillis
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A context or a Proven kept past its attempt reads nothing: the executor ends each attempt when it finishes, so a
 * guarded unit of work built by hand with a kept context, or given a kept Proven, is refused.
 */
class KeptAttemptSpec : FunSpec({

    val clock = fixedUTC(millisUTC().instant())
    val home = department("Home")
    val away = department("Away")
    val employee = employee(department = home.id())
    val second = employee(department = home.id())
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
    fun ended(uow: String, id: String) = "$uow cannot read the Proven for $id: its params were built in another " +
        "attempt or another run. Build them in the params lambda of the execute call that runs this unit of work. In " +
        "a spec, build the unit of work with executionContext.forAttempt(attempt), with the context that built the " +
        "params"
    fun builtElsewhere(uow: String) = "$uow was built with an ExecutionContext other than the one its factory " +
        "received. Build the unit of work in the factory, with the ExecutionContext the factory receives"
    fun anyone(ctx: ExecutionContext, configuration: Configuration) =
        object : GuardedUnitOfWork<Principal<*>, TestProof<*>, EmployeeParams, Employee>(
            ctx, configuration, EmployeesGuarded, setOf(),
        ) {
            override fun name() = "Anyone"
            override suspend fun tryPerform(principal: Principal<*>, params: EmployeeParams) =
                changes { update(params.employee.model()) { changeDepartment(away) } }
        }

    // A plain unit of work that runs a guarded one by hand, with the params [pick] gives it.
    class Wrapper(
        ctx: ExecutionContext,
        configuration: Configuration,
        private val inner: () -> GuardedUnitOfWork<Principal<*>, TestProof<*>, EmployeeParams, Employee>,
        private val pick: (EmployeeParams) -> EmployeeParams,
    ) : UnitOfWork<Principal<*>, EmployeeParams, Employee>(ctx, configuration) {
        override fun name() = "Wrapper"
        override suspend fun tryPerform(principal: Principal<*>, params: EmployeeParams) =
            inner().tryPerform(principal, pick(params))
    }

    // Runs a guarded unit of work whose params lambda wraps a proof and then throws, and returns its context and the
    // Proven it leaked.
    suspend fun leakingRun(): Pair<ExecutionContext, Proven<TestProof<Employee>, Employee>> {
        var leaked: Proven<TestProof<Employee>, Employee>? = null
        var kept: ExecutionContext? = null
        shouldThrow<Refused> {
            uowx.execute(TestPrincipal, { ctx: ExecutionContext -> anyone(ctx.also { kept = it }, retrying) }) {
                leaked = proven(proof(employee)) { reloaded }
                throw Refused("the params lambda fails after it wraps the proof")
            }
        }
        return checkNotNull(kept) to checkNotNull(leaked)
    }

    test("a plain unit of work that builds a guarded one with its factory's context writes the proven model") {
        coEvery { repo.update(any(), any<Employee>()) } returns persisted
        val wrapping = { ctx: ExecutionContext ->
            Wrapper(ctx, Configuration.default(), { anyone(ctx, Configuration.default()) }, { it })
        }
        uowx.execute(TestPrincipal, wrapping) { EmployeeParams(proven(proof(employee)) { reloaded }) } shouldBe
            persisted
    }

    test("a guarded unit of work that runs one built with the context of an ended run reads no Proven of that run") {
        val (kept, leaked) = leakingRun()
        val delegating = { ctx: ExecutionContext ->
            object : GuardedUnitOfWork<Principal<*>, TestProof<*>, EmployeeParams, Employee>(
                ctx, Configuration.default(), EmployeesGuarded, setOf(),
            ) {
                override suspend fun tryPerform(principal: Principal<*>, params: EmployeeParams) =
                    anyone(kept, Configuration.default()).tryPerform(principal, params)
            }
        }
        shouldThrow<IllegalStateException> {
            uowx.execute(OtherPrincipal, delegating) { EmployeeParams(leaked) }
        }.message shouldBe ended("Anyone", employee.id().stringValue())
        coVerify(exactly = 0) { repo.update(any(), any<Employee>()) }
    }

    test("a plain unit of work that runs a guarded one built with the context of an ended run reads no Proven") {
        val (kept, leaked) = leakingRun()
        val wrapping = { ctx: ExecutionContext ->
            Wrapper(ctx, Configuration.default(), { anyone(kept, Configuration.default()) }, { it })
        }
        shouldThrow<IllegalStateException> {
            uowx.execute(OtherPrincipal, wrapping) { EmployeeParams(leaked) }
        }.message shouldBe ended("Anyone", employee.id().stringValue())
        coVerify(exactly = 0) { repo.update(any(), any<Employee>()) }
    }

    test("a retry of a plain unit of work that kept the first attempt's context and an unread Proven is refused") {
        coEvery { repo.update(any(), any<Employee>()) } throws StaleRecordException(setOf(second.id()), "employee")
        val first = proof(employee)
        var reloads = 0
        var kept: ExecutionContext? = null
        var unread: Proven<TestProof<Employee>, Employee>? = null
        var attempts = 0
        val wrapping = { ctx: ExecutionContext ->
            val held = kept ?: ctx.also { kept = it }
            val pick = { params: EmployeeParams -> if (attempts > 1) EmployeeParams(checkNotNull(unread)) else params }
            Wrapper(held, retrying, { anyone(held, Configuration.default()) }, pick)
        }
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, wrapping) {
                attempts++
                val forFirst = proven(first) { reloads++; reloaded }
                if (unread == null) unread = forFirst
                if (attempts == 1) EmployeeParams(proven(proof(second)) { second }) else EmployeeParams(forFirst)
            }
        }.message shouldBe ended("Anyone", employee.id().stringValue())
        attempts shouldBe 2
        reloads shouldBe 0
        first.checkedAgain shouldBe listOf()
        coVerify(exactly = 1) { repo.update(any(), any<Employee>()) }
    }

    test("a guarded unit of work that a plain one builds with a context that carries no attempt reads no Proven") {
        @OptIn(ExecutionContextApi::class)
        val detached = uowx.executionContext()
        val wrapping = { _: ExecutionContext ->
            Wrapper(detached, Configuration.default(), { anyone(detached, Configuration.default()) }, { it })
        }
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, wrapping) { EmployeeParams(proven(proof(employee)) { reloaded }) }
        }.message shouldBe "Anyone cannot read the Proven for ${employee.id().stringValue()}: it was built with an " +
            "ExecutionContext that carries no attempt. Build the unit of work in the factory of the execute call, " +
            "with the ExecutionContext the factory receives. A composed child reads no Proven. In a spec, build it " +
            "with executionContext.forAttempt(attempt)"
        coVerify(exactly = 0) { repo.update(any(), any<Employee>()) }
    }

    test("one guarded unit of work given to two execute calls is refused in the second, before the params lambda") {
        coEvery { repo.update(any(), any<Employee>()) } returns persisted
        var shared: MoveEmployeeUow? = null
        val sharing = { ctx: ExecutionContext -> shared ?: MoveEmployeeUow(ctx, setOf(), away).also { shared = it } }
        uowx.execute(TestPrincipal, sharing) { MoveEmployeeUow.Params(proven(proof(employee)) { reloaded }, "one") }
        var built = 0
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, sharing) {
                built++
                MoveEmployeeUow.Params(proven(proof(employee)) { reloaded }, "two")
            }
        }.message shouldBe builtElsewhere("MoveEmployeeUow")
        built shouldBe 0
    }

    test("the executor calls an anonymous guarded unit of work it refuses a guarded unit of work") {
        @OptIn(ExecutionContextApi::class)
        val detached = uowx.executionContext()
        val anonymous = { _: ExecutionContext ->
            object : GuardedUnitOfWork<Principal<*>, TestProof<*>, EmployeeParams, Employee>(
                detached, Configuration.default(), EmployeesGuarded, setOf(),
            ) {
                override suspend fun tryPerform(principal: Principal<*>, params: EmployeeParams) =
                    changes { update(params.employee.model()) { changeDepartment(away) } }
            }
        }
        shouldThrow<IllegalStateException> {
            uowx.execute(TestPrincipal, anonymous) { EmployeeParams(proven(proof(employee)) { reloaded }) }
        }.message shouldBe builtElsewhere("A guarded unit of work")
    }

    test("64 runs at once, each with one stale write and a retry, each read their own attempt's Proven") {
        val calls = ConcurrentHashMap<EmployeeId, AtomicInteger>()
        coEvery { repo.update(any(), any<Employee>()) } answers {
            val model = secondArg<Employee>()
            if (calls.computeIfAbsent(model.id()) { AtomicInteger() }.incrementAndGet() == 1) {
                throw StaleRecordException(setOf(model.id()), "employee")
            }
            model
        }
        val results = withContext(Dispatchers.Default) {
            (1..64).map {
                async {
                    val mine = employee(department = home.id())
                    val again = employee(id = mine.id(), department = home.id())
                    val proof = proof(mine)
                    val reloads = AtomicInteger()
                    val result = uowx.execute(TestPrincipal, { ctx: ExecutionContext -> anyone(ctx, retrying) }) {
                        EmployeeParams(proven(proof) { reloads.incrementAndGet(); again })
                    }
                    result.id() == mine.id() && reloads.get() == 1 && proof.checkedAgain.singleOrNull() === again
                }
            }.awaitAll()
        }
        results.count { it } shouldBe 64
    }
})
