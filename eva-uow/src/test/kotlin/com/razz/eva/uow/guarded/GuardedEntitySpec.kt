package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.RationAllocation
import com.razz.eva.domain.RationAllocation.Companion.todayAllocation
import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import com.razz.eva.persistence.DummyConnection
import com.razz.eva.persistence.PersistenceException.StaleRecordException
import com.razz.eva.persistence.WithCtxConnectionTransactionManager
import com.razz.eva.repository.EntityRepos
import com.razz.eva.repository.EntityRepository
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.ModelRepository
import com.razz.eva.repository.TransactionalContext.Companion.transactionalContext
import com.razz.eva.repository.hasEntityRepo
import com.razz.eva.repository.hasRepo
import com.razz.eva.uow.BaseUnitOfWork.Configuration
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.DummyEventRepository
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.ExecutionStep
import com.razz.eva.uow.ExecutionStep.EntityAdded
import com.razz.eva.uow.ExecutionStep.ModelUpdated
import com.razz.eva.uow.ExecutionStep.TransactionFinished
import com.razz.eva.uow.ExecutionStep.TransactionStarted
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.Retry.StaleRecordFixedRetry
import com.razz.eva.uow.SpyCreatableEntityRepo
import com.razz.eva.uow.SpyModelRepo
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UnitOfWorkExecutor
import com.razz.eva.uow.guarded.Fixtures.department
import com.razz.eva.uow.params.kotlinx.KotlinxParamsSerializer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.coEvery
import io.mockk.mockk
import io.opentelemetry.api.OpenTelemetry
import java.time.Duration.ofMillis

/** A guarded block adds an entity: the executor writes it once, in the transaction that writes the block's models. */
class GuardedEntitySpec : FunSpec({

    val clock = fixedUTC(millisUTC().instant())
    val context = transactionalContext(clock.instant())
    val history = mutableListOf<ExecutionStep>()
    val allocation = todayAllocation(EmployeeId(), BUBALEH, 2)
    val home = department("Home")

    @Suppress("UNCHECKED_CAST")
    fun executor(departments: ModelRepository<*, Department<*>>) = UnitOfWorkExecutor(
        listOf(),
        Persisting(
            transactionManager = WithCtxConnectionTransactionManager(
                connection = { DummyConnection },
                beforeTxn = { history.add(TransactionStarted(it)) },
                afterTxn = { mode, _ -> history.add(TransactionFinished(mode)) },
            ),
            modelRepos = ModelRepos(Department::class hasRepo departments),
            entityRepos = EntityRepos(
                RationAllocation::class hasEntityRepo
                    SpyCreatableEntityRepo(history) as EntityRepository<RationAllocation>,
            ),
            eventRepository = DummyEventRepository(),
            paramsSerializer = KotlinxParamsSerializer(),
        ),
        clock,
        OpenTelemetry.noop(),
    )

    @Suppress("UNCHECKED_CAST")
    val spied = executor(SpyModelRepo(history) as ModelRepository<*, Department<*>>)

    beforeTest { history.clear() }

    test("a block that adds only an entity returns it, and the executor persists it") {
        val added = spied.execute(TestPrincipal, { UnprovenUow(it) { add(allocation) } }) { ChildParams }
        added shouldBeSameInstanceAs allocation
        history shouldContainExactly listOf(
            TransactionStarted(REQUIRE_NEW),
            EntityAdded(context, allocation),
            TransactionFinished(REQUIRE_NEW),
        )
    }

    test("an entity the block adds is persisted in the transaction that writes the block's models") {
        val renamed = home.rename("Renamed")
        spied.execute(TestPrincipal, { UnprovenUow(it) { update(renamed); add(allocation) } }) { ChildParams }
        history shouldContainExactly listOf(
            TransactionStarted(REQUIRE_NEW),
            ModelUpdated(context, renamed),
            EntityAdded(context, allocation),
            TransactionFinished(REQUIRE_NEW),
        )
    }

    test("a retry runs the block again and persists the entity it adds once") {
        val renamed = home.rename("Renamed")
        val departments = mockk<ModelRepository<DepartmentId, Department<*>>>()
        coEvery { departments.update(any(), any<Department<*>>()) }
            .throws(StaleRecordException(setOf(home.id()), "department"))
            .andThen(renamed)
        var runs = 0
        val factory = { ctx: ExecutionContext ->
            object : GuardedUnitOfWork<TestPrincipal, TestProof<*>, ChildParams, RationAllocation>(
                ctx,
                Configuration(retry = StaleRecordFixedRetry(1, ofMillis(0))),
                EmployeesGuarded,
                setOf(Department::class),
            ) {
                override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) = changes {
                    runs++
                    update(renamed)
                    add(allocation)
                }
            }
        }
        executor(departments).execute(TestPrincipal, factory) { ChildParams } shouldBeSameInstanceAs allocation
        runs shouldBe 2
        history shouldContainExactly listOf(
            TransactionStarted(REQUIRE_NEW),
            TransactionStarted(REQUIRE_NEW),
            EntityAdded(context, allocation),
            TransactionFinished(REQUIRE_NEW),
        )
    }
})
