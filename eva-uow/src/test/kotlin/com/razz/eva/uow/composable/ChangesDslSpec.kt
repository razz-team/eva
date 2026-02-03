package com.razz.eva.uow.composable

import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.EmployeeId.Companion.randomEmployeeId
import com.razz.eva.domain.Tag
import com.razz.eva.domain.Ration
import com.razz.eva.domain.RationAllocation
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
import com.razz.eva.uow.AddEntity
import com.razz.eva.uow.AddModel
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.DeleteEntity
import com.razz.eva.uow.DeleteEntityByKey
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.TestConstantModelParam.constantModelParamForSpec
import com.razz.eva.uow.TestExecutionContext.executionContextForSpec
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UpdateModel
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.opentelemetry.api.OpenTelemetry
import java.time.LocalDate

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

        val uow = object : DummyUow<String>(executionContextForSpec(clock, OpenTelemetry.noop())) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0)
                update(model1)
                notChanged(model2)
                "K P A C U B O"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.modelChangesToPersist shouldBe listOf(
            AddModel(
                model0,
                listOf(
                    TestModelCreated(model0.id()),
                    TestModelStatusChanged(model0.id(), CREATED, ACTIVE),
                ),
            ),
            UpdateModel(model1, listOf(TestModelStatusChanged(model1.id(), CREATED, ACTIVE))),
            NoopModel(model2),
        )
        changes.result shouldBe "K P A C U B O"
    }

    test("Should throw exception when unchanged model added") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyUow<String>(executionContext) {
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

        val uow = object : DummyUow<String>(executionContext) {
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

        val uow = object : DummyUow<String>(executionContext) {
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

        val uow = object : DummyUow<String>(executionContext) {
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

        val uow = object : DummyUow<String>(executionContext) {
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

        val uow = object : DummyUow<Unit>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {}
        }

        val exception = shouldThrow<IllegalArgumentException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "No changes to persist"
    }

    test("Should return properly built RealisedChanges when unchanged model not changed") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model)
                "K P A C U B O"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.modelChangesToPersist shouldBe listOf(NoopModel(model))
        changes.result shouldBe "K P A C U B O"
    }

    test("Should throw exception when same model updated with composable uow case the same updates") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
        val innerUow0 = object : DummyUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model.changeParam1("123"))
            }
        }
        val innerUow1 = object : DummyUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model.changeParam1("123"))
            }
        }
        val uow = object : DummyUow<String>(executionContext) {
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
        val innerUow0 = object : DummyUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model.changeParam1("123").changeParam2(10L))
            }
        }
        val innerUow1 = object : DummyUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model.changeParam1("123"))
            }
        }
        val uow = object : DummyUow<String>(executionContext) {
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
        val innerUow0 = object : DummyUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model.changeParam1("123"))
            }
        }
        val innerUow1 = object : DummyUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model.changeParam1("123").changeParam2(10L))
            }
        }
        val uow = object : DummyUow<String>(executionContext) {
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

    test("Should return properly built RealisedChanges when entity is added") {
        val departmentId = randomDepartmentId()
        val tag = Tag.environmentTag(departmentId.id, "production")

        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(tag)
                "ENTITY ADDED"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.modelChangesToPersist shouldBe listOf()
        changes.entityChangesToPersist shouldBe listOf(AddEntity(tag))
        changes.result shouldBe "ENTITY ADDED"
    }

    test("Should return properly built RealisedChanges when entity is deleted") {
        val departmentId = randomDepartmentId()
        val tag = Tag.priorityTag(departmentId.id, 1)

        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                delete(tag)
                "ENTITY DELETED"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.modelChangesToPersist shouldBe listOf()
        changes.entityChangesToPersist shouldBe listOf(DeleteEntity(tag))
        changes.result shouldBe "ENTITY DELETED"
    }

    test("Should return properly built RealisedChanges when model added and entity added") {
        val model = createdTestModel("MLG", 420).activate()
        val departmentId = randomDepartmentId()
        val tag = Tag.environmentTag(departmentId.id, "staging")

        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model)
                add(tag)
                "MIXED CHANGES"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.modelChangesToPersist shouldBe listOf(
            AddModel(
                model,
                listOf(
                    TestModelCreated(model.id()),
                    TestModelStatusChanged(model.id(), CREATED, ACTIVE),
                ),
            ),
        )
        changes.entityChangesToPersist shouldBe listOf(AddEntity(tag))
        changes.result shouldBe "MIXED CHANGES"
    }

    test("Should return properly built RealisedChanges when multiple entities added and deleted") {
        val departmentId = randomDepartmentId()
        val employeeId = randomEmployeeId()
        val tag1 = Tag.environmentTag(departmentId.id, "production")
        val tag2 = Tag.priorityTag(departmentId.id, 1)
        val oldTag = Tag.tag(departmentId.id, "deprecated", "true")
        val allocation = RationAllocation.allocation(employeeId, Ration.BUBALEH, LocalDate.of(2026, 1, 1), 10)

        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(tag1)
                add(tag2)
                add(allocation)
                delete(oldTag)
                "MULTIPLE ENTITIES"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.modelChangesToPersist shouldBe listOf()
        changes.entityChangesToPersist shouldBe listOf(
            AddEntity(tag1),
            AddEntity(tag2),
            AddEntity(allocation),
            DeleteEntity(oldTag),
        )
        changes.result shouldBe "MULTIPLE ENTITIES"
    }

    test("Should merge entity changes from sub-uow") {
        val departmentId = randomDepartmentId()
        val tag1 = Tag.environmentTag(departmentId.id, "production")
        val tag2 = Tag.priorityTag(departmentId.id, 1)

        val innerUow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(tag2)
                "inner result"
            }
        }
        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(tag1)
                execute(innerUow, TestPrincipal) { Params }
                "MERGED ENTITIES"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.modelChangesToPersist shouldBe listOf()
        changes.entityChangesToPersist shouldBe listOf(AddEntity(tag1), AddEntity(tag2))
        changes.result shouldBe "MERGED ENTITIES"
    }

    test("Should return properly built RealisedChanges when entity is deleted by key") {
        val departmentId = randomDepartmentId()
        val tagKey = Tag.Key(departmentId.id, "deprecated")

        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                delete(tagKey)
                "ENTITY DELETED BY KEY"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.modelChangesToPersist shouldBe listOf()
        changes.entityChangesToPersist shouldHaveSize 1
        changes.entityChangesToPersist.first().shouldBeTypeOf<DeleteEntityByKey<Tag, Tag.Key>>()
        changes.result shouldBe "ENTITY DELETED BY KEY"
    }

    test("Should return properly built RealisedChanges when model added and entity deleted by key") {
        val model = createdTestModel("MLG", 420).activate()
        val departmentId = randomDepartmentId()
        val tagKey = Tag.Key(departmentId.id, "old-tag")

        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model)
                delete(tagKey)
                "MIXED CHANGES WITH KEY DELETE"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.modelChangesToPersist shouldBe listOf(
            AddModel(
                model,
                listOf(
                    TestModelCreated(model.id()),
                    TestModelStatusChanged(model.id(), CREATED, ACTIVE),
                ),
            ),
        )
        changes.entityChangesToPersist shouldHaveSize 1
        changes.entityChangesToPersist.first().shouldBeTypeOf<DeleteEntityByKey<Tag, Tag.Key>>()
        changes.result shouldBe "MIXED CHANGES WITH KEY DELETE"
    }

    test("Should return properly built RealisedChanges with both entity delete and key delete") {
        val departmentId = randomDepartmentId()
        val tag = Tag.environmentTag(departmentId.id, "production")
        val tagKey = Tag.Key(departmentId.id, "deprecated")

        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                delete(tag)
                delete(tagKey)
                "BOTH DELETES"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.modelChangesToPersist shouldBe listOf()
        changes.entityChangesToPersist shouldHaveSize 2
        changes.entityChangesToPersist[0] shouldBe DeleteEntity(tag)
        changes.entityChangesToPersist[1].shouldBeTypeOf<DeleteEntityByKey<Tag, Tag.Key>>()
        changes.result shouldBe "BOTH DELETES"
    }

    test("Should merge key delete changes from sub-uow") {
        val departmentId = randomDepartmentId()
        val tagKey1 = Tag.Key(departmentId.id, "tag1")
        val tagKey2 = Tag.Key(departmentId.id, "tag2")

        val innerUow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                delete(tagKey2)
                "inner result"
            }
        }
        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                delete(tagKey1)
                execute(innerUow, TestPrincipal) { Params }
                "MERGED KEY DELETES"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.modelChangesToPersist shouldBe listOf()
        changes.entityChangesToPersist shouldHaveSize 2
        changes.entityChangesToPersist[0].shouldBeTypeOf<DeleteEntityByKey<Tag, Tag.Key>>()
        changes.entityChangesToPersist[1].shouldBeTypeOf<DeleteEntityByKey<Tag, Tag.Key>>()
        changes.result shouldBe "MERGED KEY DELETES"
    }

    test("Should throw exception when notChanged called twice for the same model") {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model)
                notChanged(model)
                "unreachable"
            }
        }
        val exception = shouldThrow<IllegalStateException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "Change for a given model [${model.id()}] was already registered"
    }

    test("Should throw exception when notChanged called for model updated in inner UoW") {
        // Model updated in inner UoW becomes Dirty, outer UoW tries to mark it as notChanged
        // This should fail because model.isDirty() = true, model.isPersisted() = false, model.isNew() = false
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val innerUow = object : DummyUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model.changeParam1("modified"))
            }
        }
        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val updatedModel = execute(innerUow, TestPrincipal) { Params }
                notChanged(updatedModel) // Should throw because model is dirty
                "unreachable"
            }
        }
        val exception = shouldThrow<IllegalArgumentException> {
            uow.tryPerform(TestPrincipal, DummyUow.Params)
        }
        exception.message shouldBe "Attempted to register changed model [${model.id().stringValue()}] as unchanged"
    }

    // Tests for ModelParam - model passed via ModelParam appears persisted and produces correct ModelChange

    test("Should return AddModel when new model passed via ModelParam is updated") {
        // New model wrapped via ModelParam appears persisted, but framework knows it's new
        val newModel = createdTestModel("MLG", 420)
        val modelParam = constantModelParamForSpec(newModel)

        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val model = modelParam.model()
                // Model appears persisted due to SnapshotState
                model.isPersisted() shouldBe true
                model.isNew() shouldBe false

                // Modify and update
                val modified = model.activate()
                modified.isDirty() shouldBe true
                update(modified)
                "UPDATED VIA MODEL PARAM"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        // Should be AddModel (INSERT) because framework sees underlying NewState
        changes.modelChangesToPersist shouldHaveSize 1
        val modelChange = changes.modelChangesToPersist.first()
        modelChange.shouldBeTypeOf<AddModel<TestModelId, ActiveTestModel, TestModelEvent>>()
        modelChange.id shouldBe newModel.id()
        changes.result shouldBe "UPDATED VIA MODEL PARAM"
    }

    test("Should return UpdateModel when existing model passed via ModelParam is updated") {
        // Existing model wrapped via ModelParam appears persisted
        val existingModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
        val modelParam = constantModelParamForSpec(existingModel)

        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val model = modelParam.model()
                // Model appears persisted due to SnapshotState
                model.isPersisted() shouldBe true

                // Modify and update
                val modified = model.activate()
                modified.isDirty() shouldBe true
                update(modified)
                "UPDATED EXISTING VIA MODEL PARAM"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        // Should be UpdateModel (UPDATE) because framework sees underlying PersistedState
        changes.modelChangesToPersist shouldHaveSize 1
        val modelChange = changes.modelChangesToPersist.first()
        modelChange.shouldBeTypeOf<UpdateModel<TestModelId, ActiveTestModel, TestModelEvent>>()
        modelChange.id shouldBe existingModel.id()
        changes.result shouldBe "UPDATED EXISTING VIA MODEL PARAM"
    }

    test("Should return NoopModel when model passed via ModelParam is not changed") {
        val existingModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
        val modelParam = constantModelParamForSpec(existingModel)

        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val model = modelParam.model()
                // Model appears persisted
                model.isPersisted() shouldBe true
                notChanged(model)
                "NOT CHANGED VIA MODEL PARAM"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.modelChangesToPersist shouldHaveSize 1
        changes.modelChangesToPersist.first().shouldBeTypeOf<NoopModel>()
        changes.result shouldBe "NOT CHANGED VIA MODEL PARAM"
    }

    test("Should return AddModel with merged events when new model via ModelParam is updated in inner UoW") {
        val newModel = createdTestModel("MLG", 420)
        val modelParam = constantModelParamForSpec(newModel)

        val innerUow = object : DummyUow<ActiveTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val model = modelParam.model()
                val modified = model.changeParam1("inner").activate()
                update(modified)
            }
        }
        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                execute(innerUow, TestPrincipal) { Params }
                "INNER UOW WITH MODEL PARAM"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        // Should be AddModel because underlying state is New
        changes.modelChangesToPersist shouldHaveSize 1
        val modelChange = changes.modelChangesToPersist.first()
        modelChange.shouldBeTypeOf<AddModel<TestModelId, ActiveTestModel, TestModelEvent>>()
        modelChange.id shouldBe newModel.id()
        modelChange.modelEvents shouldBe listOf(
            TestModelCreated(newModel.id()),
            TestModelEvent1(newModel.id()),
            TestModelStatusChanged(newModel.id(), CREATED, ACTIVE),
        )
        changes.result shouldBe "INNER UOW WITH MODEL PARAM"
    }
})
