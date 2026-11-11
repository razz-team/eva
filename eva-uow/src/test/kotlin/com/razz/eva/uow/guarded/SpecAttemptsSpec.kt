package com.razz.eva.uow.guarded

import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeEvent.DepartmentChanged
import com.razz.eva.domain.TestModel
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.test.uow.UowShouldSpec
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.WrappingUnitOfWork
import com.razz.eva.uow.composable.ChangesDsl
import com.razz.eva.uow.guarded.Fixtures.department
import com.razz.eva.uow.guarded.Fixtures.employee
import com.razz.eva.uow.guarded.Fixtures.proof
import com.razz.eva.uow.guarded.MoveEmployeeUow.Params
import com.razz.eva.uow.guarded.Proven.Companion.proven
import com.razz.eva.uow.stubChanges
import com.razz.eva.uow.TestDoubleApi
import com.razz.eva.uow.verify.verifyInOrder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

// The spec base runs an attempt as the executor does, from the factory and params lambda an execute call takes.
class SpecAttemptsSpec : UowShouldSpec({

    val home = department("Home")
    val away = department("Away")
    val elsewhere = department("Elsewhere")
    val move = { ctx: ExecutionContext -> MoveEmployeeUow(ctx, setOf(), away) }
    fun plain(model: TestModel) = { ctx: ExecutionContext ->
        object : com.razz.eva.uow.UnitOfWork<TestPrincipal, ChildParams, TestModel>(ctx) {
            override fun name() = "Plain"
            override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) = changes { add(model) }
        }
    }
    fun composable(model: TestModel) = { ctx: ExecutionContext ->
        object : com.razz.eva.uow.composable.UnitOfWork<TestPrincipal, ChildParams, TestModel>(ctx) {
            override fun name() = "Composable"
            override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) = changes { add(model) }
        }
    }
    fun wrapping(model: TestModel) = { ctx: ExecutionContext ->
        object : WrappingUnitOfWork<TestPrincipal, ChildParams, TestModel, ChangesDsl>(ctx) {
            override fun name() = "Wrapping"
            final override fun wrap(dsl: ChangesDsl) = dsl
            override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams): Block =
                changes { add(model) }
        }
    }

    should("run the first attempt of a plain, a composable and a wrapping unit of work") {
        val plainModel = createdTestModel("plain", 1)
        val plainChanges = plain(plainModel).firstAttempt(TestPrincipal) { ChildParams }
        plainChanges.modelChangesToPersist.single().model shouldBeSameInstanceAs plainModel
        plainChanges.result shouldBeSameInstanceAs plainModel
        val composableModel = createdTestModel("composable", 2)
        val composableChanges = composable(composableModel).firstAttempt(TestPrincipal) { ChildParams }
        composableChanges.modelChangesToPersist.single().model shouldBeSameInstanceAs composableModel
        composableChanges.result shouldBeSameInstanceAs composableModel
        val wrappingModel = createdTestModel("wrapping", 3)
        val wrappingChanges = wrapping(wrappingModel).firstAttempt(TestPrincipal) { ChildParams }
        wrappingChanges.modelChangesToPersist.single().model shouldBeSameInstanceAs wrappingModel
        wrappingChanges.result shouldBeSameInstanceAs wrappingModel
    }

    should("write the model the proof was issued for on the first attempt of a guarded unit of work") {
        val employee = employee(department = home.id())
        val proof = proof(employee)
        move.firstAttempt(TestPrincipal) {
            Params(proven(proof) { error("the first attempt does not reload") }, "moves")
        } verifyInOrder {
            updates<Employee> { departmentId shouldBe away.id() }
            emitsEq(DepartmentChanged(employee.id(), home.id(), away.id()))
            returns { departmentId shouldBe away.id() }
        }
        proof.checkedAgain shouldBe listOf()
    }

    should("reload a proven model on a retry, ask the proof again and write the reload") {
        val employee = employee(department = home.id())
        val reloaded = employee(id = employee.id(), department = elsewhere.id())
        val proof = proof(employee)
        var reloads = 0
        move.retry(TestPrincipal) { Params(proven(proof) { reloads++; reloaded }, "moves") } verifyInOrder {
            updates<Employee> { departmentId shouldBe away.id() }
            emitsEq(DepartmentChanged(employee.id(), elsewhere.id(), away.id()))
            returns { departmentId shouldBe away.id() }
        }
        reloads shouldBe 1
        proof.checkedAgain shouldBe listOf(reloaded)
    }

    should("refuse a retry whose reload the proof refuses") {
        val employee = employee(department = home.id())
        val proof = proof(employee, refused = setOf(elsewhere))
        shouldThrow<Refused> {
            move.retry(TestPrincipal) { Params(proven(proof) { employee(employee.id(), elsewhere.id()) }, "moves") }
        }.message shouldBe "moved out of reach"
    }

    should("refuse a guarded unit of work its factory did not build with the context it got") {
        val employee = employee(department = home.id())
        val kept = { _: ExecutionContext -> MoveEmployeeUow(executionContext, setOf(), away) }
        shouldThrow<IllegalStateException> {
            kept.firstAttempt(TestPrincipal) { Params(proven(proof(employee)) { employee }, "moves") }
        }.message shouldBe "MoveEmployeeUow was built with an ExecutionContext other than the one its factory " +
            "received. Build the unit of work in the factory, with the ExecutionContext the factory receives"
    }

    should("refuse what the executor refuses: stubbed changes, another unit's block, an unregistered result") {
        val model = createdTestModel("added", 1)
        val stubbed = { ctx: ExecutionContext ->
            object : com.razz.eva.uow.composable.UnitOfWork<TestPrincipal, ChildParams, TestModel>(ctx) {
                override fun name() = "Stubbed"
                @OptIn(TestDoubleApi::class)
                override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) =
                    stubChanges<TestModel>(model)
            }
        }
        shouldThrow<IllegalStateException> { stubbed.firstAttempt(TestPrincipal) { ChildParams } }.message shouldBe
            "Stubbed returned stubChanges; it builds test doubles only, a real UnitOfWork must go through changes { }"
        val other = wrapping(model)
        val returningOther = { ctx: ExecutionContext ->
            object : com.razz.eva.uow.composable.UnitOfWork<TestPrincipal, ChildParams, TestModel>(ctx) {
                override fun name() = "Returning"
                override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) =
                    other(ctx).tryPerform(principal, params)
            }
        }
        shouldThrow<IllegalStateException> { returningOther.firstAttempt(TestPrincipal) { ChildParams } }
            .message shouldBe "Returning returned the changes { } block of another unit of work [Wrapping]; return " +
            "the block its own changes { } built"
        val unregistered = createdTestModel("unregistered", 2)
        val returningUnregistered = { ctx: ExecutionContext ->
            object : com.razz.eva.uow.composable.UnitOfWork<TestPrincipal, ChildParams, TestModel>(ctx) {
                override fun name() = "Unregistered"
                override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) = changes {
                    add(model)
                    unregistered
                }
            }
        }
        shouldThrow<IllegalStateException> { returningUnregistered.firstAttempt(TestPrincipal) { ChildParams } }
            .message shouldBe "Unregistered new model [${unregistered.id().stringValue()}] in the result: the write " +
            "would be silently dropped"
    }
})
