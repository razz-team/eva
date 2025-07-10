package com.razz.eva.uow

import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.TestModelEvent.TestModelCreated
import com.razz.eva.domain.TestModelEvent.TestModelStatusChanged
import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
import com.razz.eva.domain.TestModelStatus.ACTIVE
import com.razz.eva.domain.TestModelStatus.CREATED
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.OpenTelemetry

class ChangesDslSpec : FunSpec({

    val now = millisUTC().instant()
    val clock = fixedUTC(now)
    val executionContext = ExecutionContext(clock, OpenTelemetry.noop())

    test("""
        Should return properly built RealisedChanges when
        new model added, changed model updated and not changed model marked as not changed
    """) {
        val model0 = createdTestModel("MLG", 420).activate()
        val model1 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
            .activate()
        val model2 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyUow(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0)
                update(model1)
                notChanged(model2)
                "K P A C U B O"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.toPersist shouldBe listOf(
            Add(
                model0,
                listOf(
                    TestModelCreated(model0.id()),
                    TestModelStatusChanged(model0.id(), CREATED, ACTIVE)
                )
            ),
            Update(model1, listOf(TestModelStatusChanged(model1.id(), CREATED, ACTIVE))),
            Noop(model2)
        )
        changes.result shouldBe "K P A C U B O"
    }

    test("Should return properly built RealisedChanges when unchanged model updated") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyUow(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model)
                "K P A C U B O"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.toPersist shouldBe listOf(Noop(model))
        changes.result shouldBe "K P A C U B O"
    }

    test("Should throw exception when unchanged model required updated") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyUow(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model, required = true)
                "K P A C U B O"
            }
        }
        val exception = shouldThrow<IllegalArgumentException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "Attempted to register unchanged model [${model.id().stringValue()}] as changed"
    }

    test("Should throw exception when unchanged model added") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyUow(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model)
                "K P A C U B O"
            }
        }
        val exception = shouldThrow<IllegalArgumentException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "Attempted to register unchanged model [${model.id().stringValue()}] as new"
    }

    test("Should throw exception when changed model added") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1).activate()

        val uow = object : DummyUow(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model)
                "K P A C U B O"
            }
        }
        val exception = shouldThrow<IllegalArgumentException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "Attempted to register changed model [${model.id().stringValue()}] as new"
    }

    test("Should throw exception when changed model marked as not changed") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1).activate()

        val uow = object : DummyUow(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model)
                "K P A C U B O"
            }
        }
        val exception = shouldThrow<IllegalArgumentException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "Attempted to register changed model [${model.id().stringValue()}] as unchanged"
    }

    test("Should throw exception when new model marked as not changed") {
        val model = createdTestModel("MLG", 420).activate()

        val uow = object : DummyUow(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model)
                "K P A C U B O"
            }
        }
        val exception = shouldThrow<IllegalArgumentException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "Attempted to register new model [${model.id().stringValue()}] as unchanged"
    }

    test("Should throw exception when new model updated") {
        val model = createdTestModel("MLG", 420).activate()

        val uow = object : DummyUow(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model)
                "K P A C U B O"
            }
        }
        val exception = shouldThrow<IllegalArgumentException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "Attempted to register new model [${model.id().stringValue()}] as changed"
    }

    test("Should throw exception when no models were added or updated with result") {

        val uow = object : DummyUow(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                "K P A C U B O"
            }
        }

        val exception = shouldThrow<IllegalArgumentException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "No changes to persist"
    }

    test("Should throw exception when no models were added or updated without result") {

        val uow = object : DummyUnitUow(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {}
        }

        val exception = shouldThrow<IllegalArgumentException> {
            uow.tryPerform(TestPrincipal, DummyUnitUow.Params)
        }
        exception.message shouldBe "No changes to persist"
    }

    test("Should return properly built RealisedChanges when unchanged model not changed") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyUow(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model)
                "K P A C U B O"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.toPersist shouldBe listOf(Noop(model))
        changes.result shouldBe "K P A C U B O"
    }
})
