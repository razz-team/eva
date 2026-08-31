package com.razz.eva.uow.composable

import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Tag
import com.razz.eva.domain.TestModel.ActiveTestModel
import com.razz.eva.domain.TestModel.CreatedTestModel
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModelEvent
import com.razz.eva.domain.TestModelEvent.TestModelCreated
import com.razz.eva.domain.TestModelEvent.TestModelStatusChanged
import com.razz.eva.domain.TestModelId
import com.razz.eva.domain.TestModelStatus.ACTIVE
import com.razz.eva.domain.TestModelStatus.CREATED
import com.razz.eva.uow.AddEntity
import com.razz.eva.uow.AddModel
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.DeleteEntity
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.PersistedLookup
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UpdateEntity
import com.razz.eva.uow.resultOnly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.opentelemetry.api.OpenTelemetry

// The forgotten-registration branch itself (a block ending on an unregistered model) is a compile
// error by construction: the block must produce Registered<RESULT> and only the DSL mints it.
// These tests cover what still has to compile and behave.
class RegisteringUnitOfWorkSpec : FunSpec({

    val now = millisUTC().instant()
    val clock = fixedUTC(now)
    val executionContext = ExecutionContext(clock, OpenTelemetry.noop())

    test("Entity changes pass through unwrapped while model registrations return Registered") {
        val departmentId = randomDepartmentId()
        val tag1 = Tag.environmentTag(departmentId.id, "staging")
        val tag2 = Tag.priorityTag(departmentId.id, 5)
        val tag3 = Tag.tag(departmentId.id, "region", "eu-west")
        val model0 = createdTestModel("MLG", 420)

        val uow = object : DummyRegisteringUow<TestModelId>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(tag1)
                update(tag2)
                delete(tag3)
                add(model0).map { it.id() }
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyRegisteringUow.Params)

        changes.modelChangesToPersist shouldBe listOf(AddModel(model0, listOf(TestModelCreated(model0.id()))))
        changes.entityChangesToPersist shouldBe listOf(AddEntity(tag1), UpdateEntity(tag2), DeleteEntity(tag3))
        changes.result shouldBe model0.id()
    }

    test("execute passes the child result through unwrapped and merges child changes") {
        val model0 = createdTestModel("MLG", 420)

        val innerUow = { ctx: ExecutionContext ->
            object : DummyUow<CreatedTestModel>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                    add(model0)
                }
            }
        }
        val uow = object : DummyRegisteringUow<ActiveTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val added = execute(innerUow, TestPrincipal) { DummyUow.Params }
                update(added.activate())
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyRegisteringUow.Params)

        changes.modelChangesToPersist shouldHaveSize 1
        val add = changes.modelChangesToPersist.first()
            .shouldBeTypeOf<AddModel<TestModelId, ActiveTestModel, TestModelEvent>>()
        add.id shouldBe model0.id()
        add.modelEvents shouldBe listOf(
            TestModelCreated(model0.id()),
            TestModelStatusChanged(model0.id(), CREATED, ACTIVE),
        )
    }

    test("A registering child composes under a plain composable parent") {
        val model0 = createdTestModel("MLG", 420)

        val innerUow = { ctx: ExecutionContext ->
            object : DummyRegisteringUow<CreatedTestModel>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                    add(model0)
                }
            }
        }
        val uow = object : DummyUow<String>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val added = execute(innerUow, TestPrincipal) { DummyRegisteringUow.Params }
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

    test("A registering child composes under a registering parent") {
        val model0 = createdTestModel("MLG", 420)

        val innerUow = { ctx: ExecutionContext ->
            object : DummyRegisteringUow<CreatedTestModel>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                    add(model0)
                }
            }
        }
        val uow = object : DummyRegisteringUow<ActiveTestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                val added = execute(innerUow, TestPrincipal) { DummyRegisteringUow.Params }
                update(added.activate())
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyRegisteringUow.Params)

        changes.modelChangesToPersist shouldHaveSize 1
        val add = changes.modelChangesToPersist.first()
            .shouldBeTypeOf<AddModel<TestModelId, ActiveTestModel, TestModelEvent>>()
        add.id shouldBe model0.id()
        add.modelEvents shouldBe listOf(
            TestModelCreated(model0.id()),
            TestModelStatusChanged(model0.id(), CREATED, ACTIVE),
        )
    }

    test("roundtrip passes through unwrapped and keeps its builder, resultOnly states the claim") {
        val model0 = createdTestModel("MLG", 420)

        val uow = object : DummyRegisteringUow<RegisteringRoundtripResult>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(model0)
                resultOnly(roundtrip { p -> RegisteringRoundtripResult(p(model0), "label") })
            }
        }
        val changes = uow.tryPerform(TestPrincipal, DummyRegisteringUow.Params)

        changes.result shouldBe RegisteringRoundtripResult(model0, "label")
        val emptyLookup = object : PersistedLookup {
            override fun <M : com.razz.eva.domain.Model<*, *>> invoke(model: M): M = model
        }
        changes.resultBuilder.shouldNotBeNull().invoke(emptyLookup) shouldBe
            RegisteringRoundtripResult(model0, "label")
    }
})

private data class RegisteringRoundtripResult(val model: com.razz.eva.domain.TestModel, val label: String)
