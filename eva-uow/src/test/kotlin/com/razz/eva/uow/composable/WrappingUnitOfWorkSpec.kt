package com.razz.eva.uow.composable

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.TestModel
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UnitOfWorkExecutor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.OpenTelemetry
import java.time.Instant.ofEpochMilli

internal class FamilyDsl(private val dsl: ChangesDsl) {

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> = dsl.add(model)

    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> = dsl.update(model)
}

// A family that counts how often it wraps a block, records what finish saw, and refuses a block on request.
internal abstract class CountingFamily<R : Any>(
    executionContext: ExecutionContext,
    private val refuse: Boolean,
) : com.razz.eva.uow.WrappingUnitOfWork<TestPrincipal, DummyUow.Params, R, FamilyDsl>(executionContext) {
    var wraps = 0
    var finished: Any? = null

    override fun wrap(dsl: ChangesDsl): FamilyDsl {
        wraps += 1
        return FamilyDsl(dsl)
    }

    override fun finish(dsl: FamilyDsl, result: R) {
        finished = result
        check(!refuse) { "The family refused the block" }
    }
}

internal abstract class ChildFamily<R : Any>(executionContext: ExecutionContext) :
    WrappingUnitOfWork<TestPrincipal, DummyUow.Params, R, FamilyDsl>(executionContext) {
    override fun wrap(dsl: ChangesDsl) = FamilyDsl(dsl)
}

class WrappingUnitOfWorkSpec : FunSpec({

    val executionContext = ExecutionContext(fixedUTC(ofEpochMilli(0)), OpenTelemetry.noop())

    fun counting(refuse: Boolean, block: suspend FamilyDsl.() -> TestModel) = { exCtx: ExecutionContext ->
        object : CountingFamily<TestModel>(exCtx, refuse) {
            override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) = changes(block)
        }
    }

    test("wrap runs once for each changes block, and finish sees the block's result") {
        val created = createdTestModel("new", 1)
        val uow = counting(refuse = false) { add(created) }(executionContext)
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)
        uow.wraps shouldBe 1
        uow.finished shouldBeSameInstanceAs created
        changes.modelChangesToPersist.map { it.model } shouldBe listOf(created)
    }

    test("A block that finish refuses fails, and nothing is written") {
        val persisting = mockk<Persisting>(relaxed = true)
        val executor = UnitOfWorkExecutor(listOf(), persisting, fixedUTC(ofEpochMilli(0)), OpenTelemetry.noop())
        val ex = shouldThrow<IllegalStateException> {
            executor.execute(TestPrincipal, counting(refuse = true) { add(createdTestModel("new", 1)) }) {
                DummyUow.Params
            }
        }
        checkNotNull(ex.message) shouldContain "The family refused the block"
        verify { persisting wasNot Called }
    }

    test("A family's DSL cannot skip eva's checks: adding a model that is not new fails") {
        val existing = existingCreatedTestModel(param1 = "stored", param2 = 1)
        val uow = counting(refuse = false) { add(existing) }(executionContext)
        val ex = shouldThrow<IllegalArgumentException> { uow.tryPerform(TestPrincipal, DummyUow.Params) }
        checkNotNull(ex.message) shouldContain "as new"
    }

    test("A composable family's unit of work runs as a child, and its changes merge into the parent's") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val first = read.changeParam1("parent")
        val extended = first.changeParam2(2)
        val created = createdTestModel("child", 1)
        val child = { exCtx: ExecutionContext ->
            object : ChildFamily<TestModel>(exCtx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) = changes {
                    add(created)
                    update(extended)
                }
            }
        }
        val parent = object : DummyUow<TestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(first)
                execute(child, TestPrincipal) { Params }
            }
        }
        val changes = parent.tryPerform(TestPrincipal, DummyUow.Params)
        changes.modelChangesToPersist.map { it.model } shouldBe listOf(extended, created)
        changes.modelChangesToPersist.map { it.modelEvents.size } shouldBe listOf(2, 1)
    }
})
