package com.razz.eva.uow.composable

import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Tag
import com.razz.eva.domain.TestModel
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.events.UowEvent
import com.razz.eva.uow.AddEntity
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.PersistedLookup
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UnitOfWorkExecutor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.Called
import io.mockk.coEvery
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

    final override fun wrap(dsl: ChangesDsl): FamilyDsl {
        wraps += 1
        return FamilyDsl(dsl)
    }

    final override fun finish(dsl: FamilyDsl, result: R) {
        finished = result
        check(!refuse) { "The family refused the block" }
    }
}

internal abstract class ChildFamily<R : Any>(executionContext: ExecutionContext) :
    WrappingUnitOfWork<TestPrincipal, DummyUow.Params, R, FamilyDsl>(executionContext) {
    final override fun wrap(dsl: ChangesDsl) = FamilyDsl(dsl)
}

internal class WideDsl(private val dsl: ChangesDsl) {
    fun add(model: TestModel): TestModel = dsl.add(model)
    fun update(model: TestModel): TestModel = dsl.update(model)
    fun add(tag: Tag): Tag = dsl.add(tag)
    fun <R> roundtrip(build: (PersistedLookup) -> R): R = dsl.roundtrip(build)
}

// finish registers a model of its own, which only works while the block's changes are not built yet
internal abstract class WideFamily<R : Any>(
    executionContext: ExecutionContext,
    private val atFinish: TestModel?,
) : com.razz.eva.uow.WrappingUnitOfWork<TestPrincipal, DummyUow.Params, R, WideDsl>(executionContext) {
    final override fun wrap(dsl: ChangesDsl) = WideDsl(dsl)
    final override fun finish(dsl: WideDsl, result: R) {
        atFinish?.let(dsl::add)
    }
}

internal abstract class WideChildFamily<R : Any>(executionContext: ExecutionContext) :
    WrappingUnitOfWork<TestPrincipal, DummyUow.Params, R, WideDsl>(executionContext) {
    final override fun wrap(dsl: ChangesDsl) = WideDsl(dsl)
}

// A unit of work of the plain family: it extends com.razz.eva.uow.UnitOfWork, which is not composable.
internal class PlainTestUow(executionContext: ExecutionContext, private val model: TestModel) :
    com.razz.eva.uow.UnitOfWork<TestPrincipal, DummyUow.Params, TestModel>(executionContext) {
    override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) = changes { add(model) }
}

class WrappingUnitOfWorkSpec : FunSpec({

    val executionContext = ExecutionContext(fixedUTC(ofEpochMilli(0)), OpenTelemetry.noop())

    fun persistingReturning(vararg flushed: Model<*, *>): Persisting {
        val persisting = mockk<Persisting>(relaxed = true)
        coEvery {
            persisting.persist(
                uowName = any(),
                params = DummyUow.Params,
                principal = TestPrincipal,
                modelChanges = any(),
                entityChanges = any(),
                now = any(),
                uowSupportsOutOfOrderPersisting = any(),
                connectionMode = any(),
            )
        } returns Pair(
            UowEvent(
                id = UowEvent.Id.random(),
                uowName = UowEvent.UowName("WrappingUow"),
                principal = TestPrincipal,
                modelEvents = listOf(),
                idempotencyKey = null,
                params = "{}",
                occurredAt = ofEpochMilli(0),
            ),
            flushed.toList(),
        )
        return persisting
    }

    fun executor(persisting: Persisting) =
        UnitOfWorkExecutor(listOf(), persisting, fixedUTC(ofEpochMilli(0)), OpenTelemetry.noop())

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

    test("The executor refuses a unit of work that returns the block another unit of its family built") {
        val other = counting(refuse = false) { add(createdTestModel("other", 1)) }(executionContext)
        val persisting = mockk<Persisting>(relaxed = true)
        val ex = shouldThrow<IllegalStateException> {
            executor(persisting).execute(
                TestPrincipal,
                { exCtx: ExecutionContext ->
                    object : CountingFamily<TestModel>(exCtx, refuse = true) {
                        override fun name() = "SmugglingUow"
                        override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) =
                            other.tryPerform(principal, params)
                    }
                },
            ) { DummyUow.Params }
        }
        checkNotNull(ex.message) shouldContain "SmugglingUow returned the changes { } block of another unit of work"
        verify { persisting wasNot Called }
    }

    test("A composable parent refuses a child that returns the block another unit built") {
        val other = object : ChildFamily<TestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) =
                changes { add(createdTestModel("other", 1)) }
        }
        val child = { exCtx: ExecutionContext ->
            object : ChildFamily<TestModel>(exCtx) {
                override fun name() = "SmugglingChildUow"
                override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) =
                    other.tryPerform(principal, params)
            }
        }
        val parent = object : DummyUow<TestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                execute(child, TestPrincipal) { Params }
            }
        }
        val ex = shouldThrow<IllegalStateException> { parent.tryPerform(TestPrincipal, DummyUow.Params) }
        checkNotNull(ex.message) shouldContain
            "SmugglingChildUow returned the changes { } block of another unit of work"
    }

    test("A composable parent refuses a child that is not composable, even past an unchecked cast") {
        val created = createdTestModel("plain", 1)
        @Suppress("UNCHECKED_CAST")
        val child = { exCtx: ExecutionContext -> PlainTestUow(exCtx, created) } as
            (ExecutionContext) -> DummyUow<TestModel>
        val parent = object : DummyUow<TestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                execute(child, TestPrincipal) { Params }
            }
        }
        val ex = shouldThrow<IllegalStateException> { parent.tryPerform(TestPrincipal, DummyUow.Params) }
        checkNotNull(ex.message) shouldContain "PlainTestUow is not composable"
    }

    test("A roundtrip { } in a family block is rerun by the executor over the persisted models") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val flushed = existingCreatedTestModel(id = read.id(), param1 = "flushed", param2 = 1)
        val result = executor(persistingReturning(flushed)).execute(
            TestPrincipal,
            { exCtx: ExecutionContext ->
                object : WideFamily<Pair<TestModel, String>>(exCtx, atFinish = null) {
                    override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) = changes {
                        val updated = update(read.changeParam1("mutated"))
                        roundtrip { p -> Pair(p<TestModel>(updated), "label") }
                    }
                }
            },
        ) { DummyUow.Params }
        result.first shouldBeSameInstanceAs flushed
    }

    test("What finish registers is part of the block's changes") {
        val inBlock = createdTestModel("block", 1)
        val atFinish = createdTestModel("finish", 2)
        val uow = object : WideFamily<TestModel>(executionContext, atFinish) {
            override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) =
                changes { add(inBlock) }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)
        changes.modelChangesToPersist.map { it.model } shouldBe listOf(inBlock, atFinish)
    }

    test("A composable family's child keeps a parent change it does not touch") {
        val parentOnly = createdTestModel("parent only", 1)
        val created = createdTestModel("child", 1)
        val child = { exCtx: ExecutionContext ->
            object : WideChildFamily<TestModel>(exCtx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) =
                    changes { add(created) }
            }
        }
        val parent = object : DummyUow<TestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(parentOnly)
                execute(child, TestPrincipal) { Params }
            }
        }
        val changes = parent.tryPerform(TestPrincipal, DummyUow.Params)
        changes.modelChangesToPersist.map { it.model } shouldBe listOf(parentOnly, created)
    }

    test("A Block carries the entity changes of its block") {
        val tag = Tag.environmentTag(randomDepartmentId().id, "staging")
        val uow = object : WideFamily<String>(executionContext, atFinish = null) {
            override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) = changes {
                add(tag)
                "ok"
            }
        }
        uow.tryPerform(TestPrincipal, DummyUow.Params).entityChangesToPersist shouldBe listOf(AddEntity(tag))
    }
})
