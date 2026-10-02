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
