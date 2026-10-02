package com.razz.eva.uow.composable

import com.razz.eva.domain.TestModel
import com.razz.eva.domain.TestModel.CreatedTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.events.UowEvent
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UnitOfWorkExecutor
import com.razz.eva.uow.verify.verifyInOrder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.OpenTelemetry
import java.time.Instant.ofEpochMilli

// The executor's two result nets over the merged change set: an unregistered write in the result
// fails, and a registered id in the result must not carry a write its registered instance lacks.
class ResultNetsSpec : FunSpec({

    fun persistingReturning(vararg flushed: TestModel): Persisting {
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
        } returns Pair(uowEvent(), flushed.toList())
        return persisting
    }

    fun executor(persisting: Persisting) =
        UnitOfWorkExecutor(listOf(), persisting, fixedUTC(ofEpochMilli(0)), OpenTelemetry.noop())

    fun <R : Any> uow(block: suspend ChangesDsl.() -> R) = { exCtx: ExecutionContext ->
        object : DummyUow<R>(exCtx) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes(block)
        }
    }

    test("Returning the model it read after registering its mutation hands the caller the persisted state") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val flushed = existingCreatedTestModel(id = read.id(), param1 = "flushed", param2 = 1)
        val result = executor(persistingReturning(flushed)).execute(
            TestPrincipal,
            uow<TestModel> {
                update(read.changeParam1("mutated"))
                read
            },
        ) { DummyUow.Params }
        result shouldBeSameInstanceAs flushed
    }

    test("A roundtrip seed superseded by a composed child's mutation resolves to the persisted state") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val flushed = existingCreatedTestModel(id = read.id(), param1 = "flushed", param2 = 1)
        val child = uow<CreatedTestModel> { update(read.changeParam1("child")) }
        val result = executor(persistingReturning(flushed)).execute(
            TestPrincipal,
            uow {
                notChanged(read)
                val seed = roundtrip { p -> Pair(p<TestModel>(read), "label") }
                execute(child, TestPrincipal) { DummyUow.Params }
                seed
            },
        ) { DummyUow.Params }
        result.first shouldBeSameInstanceAs flushed
    }

    test("A dirty ancestor of the registered instance passes and is replaced by the persisted state") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val flushed = existingCreatedTestModel(id = read.id(), param1 = "flushed", param2 = 2)
        val first = read.changeParam1("first")
        val result = executor(persistingReturning(flushed)).execute(
            TestPrincipal,
            uow<TestModel> {
                update(first.changeParam2(2))
                first
            },
        ) { DummyUow.Params }
        result shouldBeSameInstanceAs flushed
    }

    test("A dirty roundtrip seed extended by a composed child resolves to the persisted state") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val flushed = existingCreatedTestModel(id = read.id(), param1 = "flushed", param2 = 2)
        val result = executor(persistingReturning(flushed)).execute(
            TestPrincipal,
            uow {
                val first = update(read.changeParam1("parent"))
                val seed = roundtrip { p -> Pair(p<TestModel>(first), "label") }
                execute(uow<CreatedTestModel> { update(first.changeParam2(2)) }, TestPrincipal) { DummyUow.Params }
                seed
            },
        ) { DummyUow.Params }
        result.first shouldBeSameInstanceAs flushed
    }

    test("A second instance of an unchanged registration passes when it is clean") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val reread = existingCreatedTestModel(id = read.id(), param1 = "read", param2 = 1)
        val result = executor(persistingReturning()).execute(
            TestPrincipal,
            uow<TestModel> {
                notChanged(read)
                reread
            },
        ) { DummyUow.Params }
        result shouldBeSameInstanceAs reread
    }

    test("A sibling mutation of a registered model fails before anything is persisted") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val persisting = persistingReturning()
        val ex = shouldThrow<IllegalStateException> {
            executor(persisting).execute(
                TestPrincipal,
                uow<TestModel> {
                    update(read.changeParam1("registered"))
                    read.changeParam1("returned")
                },
            ) { DummyUow.Params }
        }
        checkNotNull(ex.message) shouldContain "carries a write its registered instance does not"
        verify { persisting wasNot Called }
    }

    test("An unchanged registration vouches only for its instance, not for a mutation of it") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val persisting = persistingReturning()
        val ex = shouldThrow<IllegalStateException> {
            executor(persisting).execute(
                TestPrincipal,
                uow<TestModel> {
                    notChanged(read)
                    read.changeParam1("never registered")
                },
            ) { DummyUow.Params }
        }
        checkNotNull(ex.message) shouldContain "Unregistered changed model"
        verify { persisting wasNot Called }
    }

    test("An unregistered changed model nested in containers fails") {
        val registered = existingCreatedTestModel(param1 = "registered", param2 = 1)
        val stray = existingCreatedTestModel(param1 = "stray", param2 = 2)
        val persisting = persistingReturning()
        val ex = shouldThrow<IllegalStateException> {
            executor(persisting).execute(
                TestPrincipal,
                uow {
                    update(registered.changeParam1("mutated"))
                    mapOf("k" to listOf(Pair(1, stray.changeParam2(3))))
                },
            ) { DummyUow.Params }
        }
        checkNotNull(ex.message) shouldContain "Unregistered changed model [${stray.id().stringValue()}]"
        verify { persisting wasNot Called }
    }

    test("An unregistered changed model inside a triple or an array fails") {
        val registered = existingCreatedTestModel(param1 = "registered", param2 = 1)
        val stray = existingCreatedTestModel(param1 = "stray", param2 = 2)
        listOf<suspend ChangesDsl.() -> Any>(
            { Triple(1, 2, stray.changeParam2(3)) },
            { arrayOf<Any>(stray.changeParam2(4)) },
        ).forEach { tail ->
            val ex = shouldThrow<IllegalStateException> {
                executor(persistingReturning()).execute(
                    TestPrincipal,
                    uow {
                        update(registered.changeParam1("mutated"))
                        tail()
                    },
                ) { DummyUow.Params }
            }
            checkNotNull(ex.message) shouldContain "Unregistered changed model [${stray.id().stringValue()}]"
        }
    }

    test("A result that contains itself is walked once") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val looped = mutableListOf<Any>()
        looped.add(looped)
        val result = executor(persistingReturning()).execute(
            TestPrincipal,
            uow<Any> {
                update(read.changeParam1("mutated"))
                looped
            },
        ) { DummyUow.Params }
        result shouldBeSameInstanceAs looped
    }

    test("noChanges in a composed child accepts an ancestor of the parent's registration") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val first = read.changeParam1("first")
        val child = { exCtx: ExecutionContext ->
            object : DummyUow<TestModel>(exCtx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = noChanges<TestModel>(first)
            }
        }
        executor(persistingReturning(read)).execute(
            TestPrincipal,
            uow<TestModel> {
                update(first.changeParam2(2))
                execute(child, TestPrincipal) { DummyUow.Params }
            },
        ) { DummyUow.Params }
    }

    test("noChanges in a composed child refuses a sibling of the parent's registration") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val child = { exCtx: ExecutionContext ->
            object : DummyUow<TestModel>(exCtx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) =
                    noChanges<TestModel>(read.changeParam1("sibling"))
            }
        }
        val ex = shouldThrow<IllegalStateException> {
            executor(persistingReturning()).execute(
                TestPrincipal,
                uow<TestModel> {
                    update(read.changeParam1("registered"))
                    execute(child, TestPrincipal) { DummyUow.Params }
                },
            ) { DummyUow.Params }
        }
        checkNotNull(ex.message) shouldContain "to noChanges: the write would be silently dropped"
    }

    test("verifyInOrder fails a spec whose result carries a write its registration lacks") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val uow = uow<TestModel> {
            update(read.changeParam1("registered"))
            read.changeParam1("returned")
        }(ExecutionContext(fixedUTC(ofEpochMilli(0)), OpenTelemetry.noop()))
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)
        val ex = shouldThrow<IllegalStateException> {
            changes verifyInOrder {
                updates<CreatedTestModel> { }
                emits<com.razz.eva.domain.TestModelEvent.TestModelEvent1> { }
            }
        }
        checkNotNull(ex.message) shouldContain "carries a write its registered instance does not"
    }
})

private fun uowEvent() = UowEvent(
    id = UowEvent.Id.random(),
    uowName = UowEvent.UowName("ResultNetsUow"),
    principal = TestPrincipal,
    modelEvents = listOf(),
    idempotencyKey = null,
    params = "{}",
    occurredAt = ofEpochMilli(0),
)
