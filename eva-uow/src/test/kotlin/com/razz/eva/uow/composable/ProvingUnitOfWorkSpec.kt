package com.razz.eva.uow.composable

import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Tag
import com.razz.eva.domain.TestModel.ActiveTestModel
import com.razz.eva.domain.TestModel.CreatedTestModel
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.TestModelEvent
import com.razz.eva.domain.TestModelEvent.TestModelCreated
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
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.PersistedLookup
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UpdateEntity
import com.razz.eva.uow.UpdateModel
import com.razz.eva.uow.proving.UnitOfWork as AliasedUnitOfWork
import com.razz.eva.uow.proving.unit.UnitOfWork as EffectUnitOfWork
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import io.opentelemetry.api.OpenTelemetry

// The forgotten-registration branch itself (a block ending on an unregistered model) is a compile
// error by construction; ProvingCompileRejectionSpec pins that in CI. These tests cover what still
// has to compile and behave, and the runtime guards backing the compile-time check.
class ProvingUnitOfWorkSpec : FunSpec({

    val now = millisUTC().instant()
    val clock = fixedUTC(now)
    val executionContext = ExecutionContext(clock, OpenTelemetry.noop())

    test("Block ending on a registered add produces the same changes a plain UnitOfWork would") {
        val model0 = createdTestModel("MLG", 420)

        val uow = object : DummyProvingUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0)
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.modelChangesToPersist shouldBe listOf(AddModel(model0, listOf(TestModelCreated(model0.id()))))
        changes.result shouldBe model0
    }

    test("notChanged registers and returns the persisted model") {
        val model0 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyProvingUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model0)
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.modelChangesToPersist shouldBe listOf(NoopModel(model0))
        changes.result shouldBe model0
    }

    test("noChanges(result) from the base class is still available") {
        val uow = object : DummyProvingUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) =
                noChanges("NOTHING TO DO")
        }
        val changes = uow.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.modelChangesToPersist shouldBe listOf()
        changes.result shouldBe "NOTHING TO DO"
    }

    test("noChanges rejects a changed model instead of silently dropping the write") {
        val model0 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyProvingUow<ActiveTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) =
                noChanges(model0.activate())
        }
        val exception = shouldThrow<IllegalArgumentException> {
            uow.tryPerform(TestPrincipal, DummyProvingUow.Params)
        }
        exception.message shouldBe "Attempted to pass changed model [${model0.id().stringValue()}] " +
            "to noChanges: the write would be silently dropped"
    }

    test("Entity changes pass through bare while model registrations return Accounted") {
        val departmentId = randomDepartmentId()
        val tag1 = Tag.environmentTag(departmentId.id, "staging")
        val tag2 = Tag.priorityTag(departmentId.id, 5)
        val tag3 = Tag.tag(departmentId.id, "region", "eu-west")
        val model0 = createdTestModel("MLG", 420)

        val uow = object : DummyProvingUow<TestModelId>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(tag1)
                update(tag2)
                delete(tag3)
                add(model0).map { it.id() }
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.modelChangesToPersist shouldBe listOf(AddModel(model0, listOf(TestModelCreated(model0.id()))))
        changes.entityChangesToPersist shouldBe listOf(AddEntity(tag1), UpdateEntity(tag2), DeleteEntity(tag3))
        changes.result shouldBe model0.id()
    }

    test("An unregistered new model hidden in a collection fails the block") {
        val model0 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
        val hidden = createdTestModel("MLG", 420)

        val uow = object : DummyProvingUow<List<CreatedTestModel>>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model0)
                noModelResult(listOf(hidden))
            }
        }
        val exception = shouldThrow<IllegalStateException> {
            uow.tryPerform(TestPrincipal, DummyProvingUow.Params)
        }
        exception.message shouldBe "Unregistered new model [${hidden.id().stringValue()}] " +
            "in the result: the write would be silently dropped"
    }

    test("An unregistered dirty model hidden in nested containers fails the block") {
        val model0 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
        val hidden = existingCreatedTestModel(randomTestModelId(), "smuggle", 1, V1).activate()

        val uow = object : DummyProvingUow<Map<String, List<ActiveTestModel>>>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model0)
                noModelResult(mapOf("k" to listOf(hidden)))
            }
        }
        val exception = shouldThrow<IllegalStateException> {
            uow.tryPerform(TestPrincipal, DummyProvingUow.Params)
        }
        exception.message shouldBe "Unregistered changed model [${hidden.id().stringValue()}] " +
            "in the result: the write would be silently dropped"
    }

    test("map cannot smuggle a fresh mutation of a registered model") {
        val model0 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyProvingUow<List<ActiveTestModel>>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model0).map { listOf(it.activate()) }
            }
        }
        val exception = shouldThrow<IllegalStateException> {
            uow.tryPerform(TestPrincipal, DummyProvingUow.Params)
        }
        exception.message shouldBe "Unregistered changed model [${model0.id().stringValue()}] " +
            "in the result: the write would be silently dropped"
    }

    test("Adoption via the proving.UnitOfWork alias is an import away, and Unit blocks end on Unit") {
        val model0 = createdTestModel("MLG", 420)

        val uow = object : AliasedUnitOfWork<TestPrincipal, DummyProvingUow.Params, Unit>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: DummyProvingUow.Params) = changes {
                add(model0)
                Unit
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.result shouldBe Unit
        changes.modelChangesToPersist shouldBe listOf(AddModel(model0, listOf(TestModelCreated(model0.id()))))
    }

    test("An effect UoW's block is free-form: registrations, no terminal, Unit result") {
        val model0 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
        val activated = model0.activate()

        val uow = object : EffectUnitOfWork<TestPrincipal, DummyProvingUow.Params>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: DummyProvingUow.Params) = changes {
                update(activated)
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.result shouldBe Unit
        changes.modelChangesToPersist shouldBe listOf(
            UpdateModel(activated, listOf(TestModelStatusChanged(model0.id(), CREATED, ACTIVE))),
        )
    }

    test("An effect UoW block can end on the literal Unit after its registrations") {
        val model0 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
        val activated = model0.activate()

        val uow = object : EffectUnitOfWork<TestPrincipal, DummyProvingUow.Params>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: DummyProvingUow.Params) = changes {
                update(activated)
                Unit
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.result shouldBe Unit
        changes.modelChangesToPersist shouldBe listOf(
            UpdateModel(activated, listOf(TestModelStatusChanged(model0.id(), CREATED, ACTIVE))),
        )
    }

    test("An effect UoW composes as a child under a proving parent") {
        val model0 = createdTestModel("MLG", 420)
        val model1 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val effectChild = { ctx: ExecutionContext ->
            object : EffectUnitOfWork<TestPrincipal, DummyProvingUow.Params>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: DummyProvingUow.Params) =
                    changes {
                        update(model1.activate())
                    }
            }
        }
        val uow = object : DummyProvingUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val added = add(model0)
                execute(effectChild, TestPrincipal) { DummyProvingUow.Params }
                added
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.result shouldBe model0
        changes.modelChangesToPersist shouldHaveSize 2
    }

    test("A composed parent returning the child's superseded instance is rejected") {
        val model = createdTestModel("MLG", 420)
        val child = { ctx: ExecutionContext ->
            object : DummyProvingUow<CreatedTestModel>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes { add(model) }
            }
        }
        val parent = object : DummyProvingUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val fromChild = execute(child, TestPrincipal) { DummyProvingUow.Params }
                update(fromChild.activate())
                notChanged(fromChild)
            }
        }
        val exception = shouldThrow<IllegalStateException> {
            parent.tryPerform(TestPrincipal, DummyProvingUow.Params)
        }
        exception.message.shouldNotBeNull() shouldContain "is not the instance that was registered"
        exception.message.shouldNotBeNull() shouldContain "Return the value add or update handed back"
    }

    test("The same parent returning its own registration is accepted") {
        val model = createdTestModel("MLG", 420)
        val child = { ctx: ExecutionContext ->
            object : DummyProvingUow<CreatedTestModel>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes { add(model) }
            }
        }
        val parent = object : DummyProvingUow<ActiveTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val fromChild = execute(child, TestPrincipal) { DummyProvingUow.Params }
                update(fromChild.activate())
            }
        }
        val changes = parent.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.modelChangesToPersist shouldHaveSize 1
        changes.result.status() shouldBe ACTIVE
    }

    test("A roundtrip seed superseded by a composed child is rejected") {
        val model = existingCreatedTestModel(randomTestModelId(), "seed", 1, V1)
        val child = { ctx: ExecutionContext ->
            object : DummyProvingUow<ActiveTestModel>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) =
                    changes { update(model.activate()) }
            }
        }
        val uow = object : DummyProvingUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model)
                val seed = roundtrip { p -> p(model) }
                execute(child, TestPrincipal) { DummyProvingUow.Params }
                notChanged(seed)
            }
        }
        val exception = shouldThrow<IllegalStateException> {
            uow.tryPerform(TestPrincipal, DummyProvingUow.Params)
        }
        exception.message.shouldNotBeNull() shouldContain "resolve the registered instance with roundtrip"
    }

    test("map cannot swap in an instance with the same id and events but different state") {
        val id = randomTestModelId()
        val registered = existingCreatedTestModel(id, "REGISTERED", 1, V1)
        val other = existingCreatedTestModel(id, "DIFFERENT-STATE", 999, V1)

        val uow = object : DummyProvingUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(registered).map { other }
            }
        }
        val exception = shouldThrow<IllegalStateException> {
            uow.tryPerform(TestPrincipal, DummyProvingUow.Params)
        }
        exception.message.shouldNotBeNull() shouldContain "is not the instance that was registered"
    }

    test("An unchanged registration vouches only for its instance, not its id") {
        val model0 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyProvingUow<List<ActiveTestModel>>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                notChanged(model0)
                noModelResult(listOf(model0.activate()))
            }
        }
        val exception = shouldThrow<IllegalStateException> {
            uow.tryPerform(TestPrincipal, DummyProvingUow.Params)
        }
        exception.message shouldBe "Unregistered changed model [${model0.id().stringValue()}] " +
            "in the result: the write would be silently dropped"
    }

    test("accountedByChild hands a composed child's registration through as the result") {
        val model = createdTestModel("MLG", 420)
        val child = { ctx: ExecutionContext ->
            object : DummyProvingUow<CreatedTestModel>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes { add(model) }
            }
        }
        val parent = object : DummyProvingUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                accountedByChild(execute(child, TestPrincipal) { DummyProvingUow.Params })
            }
        }
        val changes = parent.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.result shouldBe model
        changes.modelChangesToPersist shouldBe listOf(AddModel(model, listOf(TestModelCreated(model.id()))))
    }

    test("accountedByChild still fails when no child registered the model") {
        val registered = createdTestModel("MLG", 420)
        val unregistered = createdTestModel("noscope", 360)

        val uow = object : DummyProvingUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(registered)
                accountedByChild(unregistered)
            }
        }
        val exception = shouldThrow<IllegalStateException> {
            uow.tryPerform(TestPrincipal, DummyProvingUow.Params)
        }
        exception.message.shouldNotBeNull() shouldContain "Unregistered new model"
    }

    test("A batch of registered models is a legal result") {
        val model0 = createdTestModel("MLG", 420)
        val model1 = createdTestModel("noscope", 360)

        val uow = object : DummyProvingUow<List<CreatedTestModel>>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0)
                add(model1)
                noModelResult(listOf(model0, model1))
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.result shouldBe listOf(model0, model1)
        changes.modelChangesToPersist shouldBe listOf(
            AddModel(model0, listOf(TestModelCreated(model0.id()))),
            AddModel(model1, listOf(TestModelCreated(model1.id()))),
        )
    }

    test("Accounted minted by another changes block is rejected") {
        val model0 = createdTestModel("MLG", 420)
        val model1 = createdTestModel("noscope", 360)
        var smuggled: Accounted<CreatedTestModel>? = null

        val minter = object : DummyProvingUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0).also { smuggled = it }
            }
        }
        minter.tryPerform(TestPrincipal, DummyProvingUow.Params)

        val smuggler = object : DummyProvingUow<CreatedTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model1)
                smuggled.shouldNotBeNull()
            }
        }
        val exception = shouldThrow<IllegalStateException> {
            smuggler.tryPerform(TestPrincipal, DummyProvingUow.Params)
        }
        exception.message shouldBe "Accounted evidence was minted by another changes block"
    }

    test("A model in the result that is not the registered instance is rejected") {
        val id = randomTestModelId()
        val registered = existingCreatedTestModel(id, "noscope", 360, V1)
        val stale = existingCreatedTestModel(id, "noscope", 360, V1)

        val uow = object : DummyProvingUow<List<CreatedTestModel>>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(registered.activate())
                noModelResult(listOf(stale))
            }
        }
        val exception = shouldThrow<IllegalStateException> {
            uow.tryPerform(TestPrincipal, DummyProvingUow.Params)
        }
        exception.message.shouldNotBeNull() shouldContain
            "Model [${id.stringValue()}] in the result is not the instance that was registered"
    }

    test("A clean unregistered model in a collection result passes: nothing to persist for it") {
        val model0 = createdTestModel("MLG", 420)
        val queried = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyProvingUow<List<CreatedTestModel>>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0)
                noModelResult(listOf(queried))
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.result shouldBe listOf(queried)
    }

    test("execute passes the child result through bare and merges child changes") {
        val model0 = createdTestModel("MLG", 420)

        val innerUow = { ctx: ExecutionContext ->
            object : DummyUow<CreatedTestModel>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                    add(model0)
                }
            }
        }
        val uow = object : DummyProvingUow<ActiveTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val added = execute(innerUow, TestPrincipal) { DummyUow.Params }
                update(added.activate())
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.modelChangesToPersist shouldHaveSize 1
        val add = changes.modelChangesToPersist.first()
            .shouldBeTypeOf<AddModel<TestModelId, ActiveTestModel, TestModelEvent>>()
        add.id shouldBe model0.id()
        add.modelEvents shouldBe listOf(
            TestModelCreated(model0.id()),
            TestModelStatusChanged(model0.id(), CREATED, ACTIVE),
        )
    }

    test("A proving child composes under a plain composable parent") {
        val model0 = createdTestModel("MLG", 420)

        val innerUow = { ctx: ExecutionContext ->
            object : DummyProvingUow<CreatedTestModel>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                    add(model0)
                }
            }
        }
        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val added = execute(innerUow, TestPrincipal) { DummyProvingUow.Params }
                update(added.activate())
                "K P A C U B O"
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)

        changes.modelChangesToPersist shouldHaveSize 1
        val add = changes.modelChangesToPersist.first()
            .shouldBeTypeOf<AddModel<TestModelId, ActiveTestModel, TestModelEvent>>()
        add.id shouldBe model0.id()
        add.modelEvents shouldBe listOf(
            TestModelCreated(model0.id()),
            TestModelStatusChanged(model0.id(), CREATED, ACTIVE),
        )
        changes.result shouldBe "K P A C U B O"
    }

    test("A proving child composes under a proving parent") {
        val model0 = createdTestModel("MLG", 420)

        val innerUow = { ctx: ExecutionContext ->
            object : DummyProvingUow<CreatedTestModel>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                    add(model0)
                }
            }
        }
        val uow = object : DummyProvingUow<ActiveTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val added = execute(innerUow, TestPrincipal) { DummyProvingUow.Params }
                update(added.activate())
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.modelChangesToPersist shouldHaveSize 1
        val add = changes.modelChangesToPersist.first()
            .shouldBeTypeOf<AddModel<TestModelId, ActiveTestModel, TestModelEvent>>()
        add.id shouldBe model0.id()
        add.modelEvents shouldBe listOf(
            TestModelCreated(model0.id()),
            TestModelStatusChanged(model0.id(), CREATED, ACTIVE),
        )
    }

    test("roundtrip passes through bare and keeps its builder, noModelResult states the claim") {
        val model0 = createdTestModel("MLG", 420)

        val uow = object : DummyProvingUow<ProvingRoundtripResult>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0)
                noModelResult(roundtrip { p -> ProvingRoundtripResult(p(model0), "label") })
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyProvingUow.Params)

        changes.result shouldBe ProvingRoundtripResult(model0, "label")
        val emptyLookup = PersistedLookup { null }
        changes.resultBuilder.shouldNotBeNull().invoke(emptyLookup) shouldBe
            ProvingRoundtripResult(model0, "label")
    }

    test("roundtrip refuses a builder that returns Accounted") {
        val model0 = createdTestModel("MLG", 420)

        val uow = object : DummyProvingUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0)
                roundtrip<Any> { noModelResult("poison") }
                noModelResult("unreached")
            }
        }
        val exception = shouldThrow<IllegalStateException> {
            uow.tryPerform(TestPrincipal, DummyProvingUow.Params)
        }
        exception.message shouldBe
            "roundtrip builder must return the bare result; end the block with noModelResult(roundtrip { ... })"
    }
})

private data class ProvingRoundtripResult(val model: com.razz.eva.domain.TestModel, val label: String)
