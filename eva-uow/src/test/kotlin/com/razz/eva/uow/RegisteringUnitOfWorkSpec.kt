package com.razz.eva.uow

import com.razz.eva.domain.TestModel.ActiveTestModel
import com.razz.eva.domain.TestModel.CreatedTestModel
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.TestModelEvent.TestModelCreated
import com.razz.eva.domain.TestModelEvent.TestModelStatusChanged
import com.razz.eva.domain.TestModelId
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

// The forgotten-registration branch itself (a block ending on an unregistered model) is a compile
// error by construction: the block must produce Registered<RESULT> and only the DSL mints it.
// These tests cover what still has to compile and behave.
class RegisteringUnitOfWorkSpec : FunSpec({

    val now = millisUTC().instant()
    val clock = fixedUTC(now)
    val executionContext = ExecutionContext(clock, OpenTelemetry.noop())

    test("Block ending on a registered add produces the same changes a plain UnitOfWork would") {
        val model0 = createdTestModel("MLG", 420)

        val uow = object : DummyRegisteringUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0)
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyRegisteringUow.Params)

        changes.modelChangesToPersist shouldBe listOf(AddModel(model0, listOf(TestModelCreated(model0.id()))))
        changes.result shouldBe model0
    }

    test("resultOnly states the exception for a result that is not a model") {
        val model0 = createdTestModel("MLG", 420)

        val uow = object : DummyRegisteringUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0)
                resultOnly("K P A C U B O")
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyRegisteringUow.Params)

        changes.modelChangesToPersist shouldBe listOf(AddModel(model0, listOf(TestModelCreated(model0.id()))))
        changes.result shouldBe "K P A C U B O"
    }

    test("map shapes the registered result into a projection") {
        val model0 = createdTestModel("MLG", 420)

        val uow = object : DummyRegisteringUow<TestModelId>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0).map { it.id() }
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyRegisteringUow.Params)

        changes.modelChangesToPersist shouldBe listOf(AddModel(model0, listOf(TestModelCreated(model0.id()))))
        changes.result shouldBe model0.id()
    }

    test("with pairs two registrations") {
        val model0 = createdTestModel("MLG", 420)
        val model1 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1).activate()

        val uow = object : DummyRegisteringUow<Pair<CreatedTestModel, ActiveTestModel>>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0) with update(model1)
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyRegisteringUow.Params)

        changes.modelChangesToPersist shouldBe listOf(
            AddModel(model0, listOf(TestModelCreated(model0.id()))),
            UpdateModel(model1, listOf(TestModelStatusChanged(model1.id(), CREATED, ACTIVE))),
        )
        changes.result shouldBe (model0 to model1)
    }

    test("notChanged registers and returns the persisted model") {
        val model0 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyRegisteringUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model0)
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyRegisteringUow.Params)

        changes.modelChangesToPersist shouldBe listOf(NoopModel(model0))
        changes.result shouldBe model0
    }

    test("update(required = true) still rejects an unchanged model at runtime") {
        val model0 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyRegisteringUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model0, required = true)
            }
        }
        val exception = shouldThrow<IllegalArgumentException> {
            uow.tryPerform(TestPrincipal, DummyRegisteringUow.Params)
        }
        exception.message shouldBe
            "Attempted to register unchanged model [${model0.id().stringValue()}] as changed"
    }

    test("noChanges(result) from the base class is still available") {
        val uow = object : DummyRegisteringUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) =
                noChanges("NOTHING TO DO")
        }
        val changes = uow.tryPerform(TestPrincipal, DummyRegisteringUow.Params)

        changes.modelChangesToPersist shouldBe listOf()
        changes.result shouldBe "NOTHING TO DO"
    }
})
