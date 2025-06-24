package com.razz.eva.uow.composable

import com.razz.eva.domain.TestModel.ActiveTestModel
import com.razz.eva.domain.TestModel.CreatedTestModel
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.TestModelEvent
import com.razz.eva.domain.TestModelEvent.TestModelCreated
import com.razz.eva.domain.TestModelEvent.TestModelEvent1
import com.razz.eva.domain.TestModelEvent.TestModelStatusChanged
import com.razz.eva.domain.TestModelId
import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
import com.razz.eva.domain.TestModelStatus.ACTIVE
import com.razz.eva.domain.TestModelStatus.CREATED
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.uow.Add
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.Noop
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.Update
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.serialization.Serializable
import java.time.Clock

internal abstract class DummyUow<T : Any>(clock: Clock) : UnitOfWork<TestPrincipal, DummyUow.Params, T>(clock) {
    @Serializable
    object Params : UowParams<Params> {
        override fun serialization() = serializer()
    }
}

class ChangesDslSpec : FunSpec({

    val now = millisUTC().instant()
    val clock = fixedUTC(now)

    test("""
        Should return properly built RealisedChanges when
        new model added, changed model updated and not changed model marked as not changed
    """) {
        val model0 = createdTestModel("MLG", 420).activate()
        val model1 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
            .activate()
        val model2 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : com.razz.eva.uow.DummyUow(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0)
                update(model1)
                notChanged(model2)
                "K P A C U B O"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, com.razz.eva.uow.DummyUow.Params)

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

    test("Should return RealisedChanges with Update change when new model updated") {
        val model = createdTestModel("MLG", 420).activate()

        val uow = object : DummyUow<String>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model)
                "K P A C U B O"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.toPersist shouldBe listOf(
            Update(model, listOf(TestModelCreated(model.id()), TestModelStatusChanged(model.id(), CREATED, ACTIVE))))
        changes.result shouldBe "K P A C U B O"
    }

    test("Should return RealisedChanges with Noop change when new model marked as not changed") {
        val model = createdTestModel("MLG", 420).activate()

        val uow = object : DummyUow<String>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model)
                "K P A C U B O"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.toPersist shouldBe listOf(Noop(model))
        changes.result shouldBe "K P A C U B O"
    }

    test("Should throw exception when unchanged model added") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyUow<String>(clock) {
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

        val uow = object : DummyUow<String>(clock) {
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

    test("Should throw exception when unchanged model updated") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyUow<String>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model)
                "K P A C U B O"
            }
        }
        val exception = shouldThrow<IllegalArgumentException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "Attempted to register unchanged model [${model.id().stringValue()}] as changed"
    }

    test("Should throw exception when changed model marked as not changed") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1).activate()

        val uow = object : DummyUow<String>(clock) {
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

    test("Should throw exception when no models were added or updated with result") {

        val uow = object : DummyUow<String>(clock) {
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

        val uow = object : DummyUow<Unit>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {}
        }

        val exception = shouldThrow<IllegalArgumentException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "No changes to persist"
    }

    test("Should return properly built RealisedChanges when unchanged model not changed") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyUow<String>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model)
                "K P A C U B O"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.toPersist shouldBe listOf(Noop(model))
        changes.result shouldBe "K P A C U B O"
    }

    test("Should return properly built RealisedChanges when new model merged with updated model") {
        val model0 = createdTestModel("MLG", 420)

        val innerUow = object : DummyUow<String>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model0.activate())
                "K P A C U B O  B H Y T P U"
            }
        }
        val uow = object : DummyUow<String>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0)
                execute(innerUow, TestPrincipal) { Params }
                "K P A C U B O"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.toPersist shouldHaveSize 1
        val add = changes.toPersist.first().shouldBeTypeOf<Add<TestModelId, ActiveTestModel, TestModelEvent>>()
        add.id shouldBe model0.id()
        add.modelEvents shouldBe listOf(
            TestModelCreated(model0.id()),
            TestModelStatusChanged(model0.id(), CREATED, ACTIVE)
        )
        changes.result shouldBe "K P A C U B O"
    }

    test("Should return properly built RealisedChanges when returned new model updated after") {
        val model0 = createdTestModel("MLG", 420)

        val innerUow = object : DummyUow<CreatedTestModel>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0)
            }
        }
        val uow = object : DummyUow<String>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val added = execute(innerUow, TestPrincipal) { Params }
                update(added.activate())
                "K P A C U B O"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.toPersist shouldHaveSize 1
        val add = changes.toPersist.first().shouldBeTypeOf<Add<TestModelId, ActiveTestModel, TestModelEvent>>()
        add.id shouldBe model0.id()
        add.modelEvents shouldBe listOf(
            TestModelCreated(model0.id()),
            TestModelStatusChanged(model0.id(), CREATED, ACTIVE)
        )
        changes.result shouldBe "K P A C U B O"
    }

    test("Should return properly built RealisedChanges when bunch of changes registered") {
        val model0 = createdTestModel("MLG", 420)
        val model1 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
            .activate()
        val model2 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val innerUow0 = object : DummyUow<CreatedTestModel>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model1)
                add(model0)
            }
        }
        val innerUow1 = object : DummyUow<CreatedTestModel>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model2)
                update(model0.changeParam1("LEET"))
            }
        }
        val uow = object : DummyUow<String>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val added = execute(innerUow0, TestPrincipal) { Params }
                val updated = execute(innerUow1, TestPrincipal) { Params }
                update(updated.activate())
                "K P A C U B O"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.toPersist shouldHaveSize 3
        val (update, add, noop) = changes.toPersist
        update.shouldBeTypeOf<Update<TestModelId, ActiveTestModel, TestModelEvent>>()
        update.id shouldBe model1.id()
        update.modelEvents shouldBe listOf(
            TestModelStatusChanged(model1.id(), CREATED, ACTIVE)
        )
        add.shouldBeTypeOf<Add<TestModelId, ActiveTestModel, TestModelEvent>>()
        add.id shouldBe model0.id()
        add.modelEvents shouldBe listOf(
            TestModelCreated(model0.id()),
            TestModelEvent1(model0.id()),
            TestModelStatusChanged(model0.id(), CREATED, ACTIVE)
        )
        noop.shouldBeTypeOf<Noop>()
        noop.id shouldBe model2.id()
        changes.result shouldBe "K P A C U B O"
    }

    test("Should throw exception when same model updated with composable uow case the same updates") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
        val innerUow0 = object : DummyUow<CreatedTestModel>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model.changeParam1("123"))
            }
        }
        val innerUow1 = object : DummyUow<CreatedTestModel>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model.changeParam1("123"))
            }
        }
        val uow = object : DummyUow<String>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                execute(innerUow0, TestPrincipal) { Params }
                execute(innerUow1, TestPrincipal) { Params }
                "K P A C U B O"
            }
        }

        val exception = shouldThrow<IllegalStateException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "Failed to merge changes for model [${model.id()}]"
    }

    test("Should throw exception when same model updated with composable uow case first updates with 2 events") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
        val innerUow0 = object : DummyUow<CreatedTestModel>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model.changeParam1("123").changeParam2(10L))
            }
        }
        val innerUow1 = object : DummyUow<CreatedTestModel>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model.changeParam1("123"))
            }
        }
        val uow = object : DummyUow<String>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                execute(innerUow0, TestPrincipal) { Params }
                execute(innerUow1, TestPrincipal) { Params }
                "K P A C U B O"
            }
        }

        val exception = shouldThrow<IllegalStateException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "Failed to merge changes for model [${model.id()}]"
    }

    test("Should throw exception when same model updated with composable uow case second updates with 2 events") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
        val innerUow0 = object : DummyUow<CreatedTestModel>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model.changeParam1("123"))
            }
        }
        val innerUow1 = object : DummyUow<CreatedTestModel>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model.changeParam1("123").changeParam2(10L))
            }
        }
        val uow = object : DummyUow<String>(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                execute(innerUow0, TestPrincipal) { Params }
                execute(innerUow1, TestPrincipal) { Params }
                "K P A C U B O"
            }
        }

        val exception = shouldThrow<IllegalStateException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "Failed to merge changes for model [${model.id()}]"
    }
})
