package com.razz.eva.uow

import com.razz.eva.domain.Employee
import com.razz.eva.domain.TestModel
import com.razz.eva.domain.TestModel.CreatedTestModel
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.mutating
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.composable.ChangesDsl
import com.razz.eva.uow.guarded.Guarding
import com.razz.eva.uow.guarded.GuardedChangesDsl
import com.razz.eva.uow.guarded.GuardedUnitOfWork
import com.razz.eva.uow.guarded.Proof
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.opentelemetry.api.OpenTelemetry

// The witnessed scopes promise the compiler to run their lambda exactly once. That a `val` assigned inside a scope
// compiles is checked by this file compiling; the tests check that each scope keeps the promise at run time.
class ScopeContractSpec : FunSpec({

    val executionContext = ExecutionContext(fixedUTC(millisUTC().instant()), OpenTelemetry.noop())
    fun stored(param1: String) = existingCreatedTestModel(param1 = param1, param2 = 1)
    fun <R : Any> composable(body: suspend ChangesDsl.() -> R) =
        object : com.razz.eva.uow.composable.UnitOfWork<TestPrincipal, DummyUow.Params, R>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) = changes(body)
        }
    fun <R : Any> guarded(body: suspend GuardedChangesDsl<Proof<*>>.() -> R) =
        object : GuardedUnitOfWork<TestPrincipal, Proof<*>, DummyUow.Params, R>(
            executionContext,
            BaseUnitOfWork.Configuration.default(),
            Guarding(Employee::class),
            setOf(TestModel::class),
        ) {
            override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) = changes { body() }
        }

    test("a val assigned inside the composable update and add scopes is read after them") {
        val model = stored("before")
        val uow = composable {
            val renamed: CreatedTestModel
            val created: CreatedTestModel
            val kept: CreatedTestModel
            update(model) { changeParam1("after").also { renamed = it } }
            add { createdTestModel("new", 2).also { created = it } }
            val other = stored("other")
            update(other) {
                if (param1 == "other") {
                    kept = this
                } else {
                    kept = changeParam1("changed")
                }
                kept
            }
            listOf(renamed, created, kept)
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)
        changes.result.map { it.param1 } shouldBe listOf("after", "new", "other")
    }

    test("the composable update scope runs its lambda once on the changed and on the declined path") {
        var changedCalls = 0
        var declinedCalls = 0
        var addCalls = 0
        composable {
            update(stored("changed")) {
                changedCalls += 1
                changeParam1("changed again")
            }
            update(stored("declined")) {
                declinedCalls += 1
                this
            }
            val created: CreatedTestModel = add {
                addCalls += 1
                createdTestModel("new", 2)
            }
            created
        }.tryPerform(TestPrincipal, DummyUow.Params)
        changedCalls shouldBe 1
        declinedCalls shouldBe 1
        addCalls shouldBe 1
    }

    test("a val assigned inside the guarded update and add scopes is read after them") {
        val model = stored("before")
        val uow = guarded {
            val renamed: CreatedTestModel
            val created: CreatedTestModel
            val kept: CreatedTestModel
            update(model) { changeParam1("after").also { renamed = it } }
            add { createdTestModel("new", 2).also { created = it } }
            val other = stored("other")
            update(other) {
                if (param1 == "other") {
                    kept = this
                } else {
                    kept = changeParam1("changed")
                }
                kept
            }
            listOf(renamed, created, kept)
        }
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)
        changes.result.map { it.param1 } shouldBe listOf("after", "new", "other")
    }

    test("the guarded update scope runs its lambda once on the changed and on the declined path") {
        var changedCalls = 0
        var declinedCalls = 0
        var addCalls = 0
        guarded {
            update(stored("changed")) {
                changedCalls += 1
                changeParam1("changed again")
            }
            update(stored("declined")) {
                declinedCalls += 1
                this
            }
            val created: CreatedTestModel = add {
                addCalls += 1
                createdTestModel("new", 2)
            }
            created
        }.tryPerform(TestPrincipal, DummyUow.Params)
        changedCalls shouldBe 1
        declinedCalls shouldBe 1
        addCalls shouldBe 1
    }

    test("both mutating scopes run their lambda once, and a val assigned inside them is read after them") {
        val model = stored("before")
        var receiverCalls = 0
        var universalCalls = 0
        val renamed: CreatedTestModel
        val universal: CreatedTestModel
        val returned = model.mutating {
            receiverCalls += 1
            changeParam1("after").also { renamed = it }
        }
        mutating {
            universalCalls += 1
            universal = model.changeParam2(5)
        }
        receiverCalls shouldBe 1
        universalCalls shouldBe 1
        returned shouldBeSameInstanceAs renamed
        universal.param2 shouldBe 5
    }
})
