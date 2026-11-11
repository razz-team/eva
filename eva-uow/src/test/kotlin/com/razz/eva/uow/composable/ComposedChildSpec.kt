package com.razz.eva.uow.composable

import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent.NameChanged
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.DeptAggregate
import com.razz.eva.domain.DeptAggregate.Companion.newDeptAggregate
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Tag
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.domain.mutating
import com.razz.eva.uow.AddModel
import com.razz.eva.uow.Changes
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.DeleteEntity
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.TestDoubleApi
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UpdateModel
import com.razz.eva.uow.stubChanges
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.OpenTelemetry
import java.time.Instant.ofEpochMilli

/**
 * A parent takes in what a composed child registered, not the list the child's flatten built. So an
 * aggregate's owned models stay with their aggregate until the block that persists them flattens it, as in
 * a single block.
 */
@OptIn(TestDoubleApi::class)
class ComposedChildSpec : FunSpec({

    val ctx = ExecutionContext(fixedUTC(ofEpochMilli(0)), OpenTelemetry.noop())
    val bossId = EmployeeId()

    fun elsewhere(name: String) =
        OwnedDepartment(randomDepartmentId(), name, bossId, 1, BUBALEH, persistentState(V1, null))

    fun <R : Any> uow(block: suspend ChangesDsl.() -> R) = { exCtx: ExecutionContext ->
        object : DummyUow<R>(exCtx) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes(block)
        }
    }

    suspend fun perform(block: suspend ChangesDsl.() -> Any): Changes<Any> =
        uow(block)(ctx).tryPerform(TestPrincipal, DummyUow.Params)

    fun newHire() = mutating { newEmployee(Name("Jo", "March"), randomDepartmentId(), "jo@test.com", BUBALEH) }

    fun storedEmployee() = Employee(
        EmployeeId(), Name("Lou", "Reed"), randomDepartmentId(), "lou@test.com", BUBALEH, persistentState(V1, null),
    )

    fun storedDept(employee: Employee) = DeptAggregate(
        randomDepartmentId(), "D0", bossId, 1, BUBALEH, listOf(employee), persistentState(V1, null),
    )

    fun Changes<*>.persisted() = modelChangesToPersist.map { it::class to it.model }

    test("A stubbed child and a real child that claim the parent's change both leave it as it is") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val changed = read.changeParam1("parent")
        val stub = { _: ExecutionContext ->
            object : DummyUow<Any>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = stubChanges<Any>(read)
            }
        }
        val real = uow<Any> { notChanged(read) }
        val outcomes = listOf(stub, real).map { child ->
            perform {
                update(changed)
                execute(child, TestPrincipal) { DummyUow.Params }
            }.modelChangesToPersist
        }
        outcomes.forEach { it shouldBe listOf(UpdateModel(changed, listOf(changed.modelEvents().single()))) }
    }

    test("A child cannot claim unchanged a model with a write the parent's change for it lacks") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val changed = read.changeParam1("parent")
        val divergent = read.changeParam1("child")
        val ex = shouldThrow<IllegalStateException> {
            perform {
                update(changed)
                execute(uow<Any> { notChanged(divergent) }, TestPrincipal) { DummyUow.Params }
            }
        }
        ex.message shouldBe "notChanged got model [${read.id().stringValue()}] with a write the change its " +
            "parent registered does not carry: the write would be silently dropped. Register it with update, or " +
            "call notChanged with the instance the parent registered"
    }

    test("A child may claim unchanged the parent's instance, a clean read, or an earlier state of it") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val changed = read.changeParam1("parent")
        val later = changed.changeParam2(2)
        listOf(later, read, changed).forEach { claimed ->
            perform {
                update(changed)
                execute(uow<Any> { update(later) }, TestPrincipal) { DummyUow.Params }
                execute(uow<Any> { notChanged(claimed) }, TestPrincipal) { DummyUow.Params }
            }.persisted() shouldBe listOf(UpdateModel::class to later)
        }
    }

    test("A stubbed child's claim of a model the parent does not hold joins the set as a claim") {
        val other = existingCreatedTestModel(param1 = "other", param2 = 2)
        val created = createdTestModel("own", 1)
        val stub = { _: ExecutionContext ->
            object : DummyUow<Any>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = stubChanges<Any>(other)
            }
        }
        perform {
            add(created)
            execute(stub, TestPrincipal) { DummyUow.Params }
        }.persisted() shouldBe listOf(AddModel::class to created, NoopModel::class to other)
    }

    test("Two children in sequence each start from the changes the one before left") {
        val first = createdTestModel("first", 1)
        val second = createdTestModel("second", 2)
        val firstExtended = first.changeParam1("extended by the second child")
        val secondExtended = second.changeParam1("extended by the parent")
        perform {
            execute(uow<Any> { add(first) }, TestPrincipal) { DummyUow.Params }
            execute(uow<Any> { update(firstExtended); add(second) }, TestPrincipal) { DummyUow.Params }
            update(secondExtended)
        }.persisted() shouldBe listOf(AddModel::class to firstExtended, AddModel::class to secondExtended)
    }

    test("A child that composes a child of its own is merged into its parent") {
        val own = createdTestModel("parent", 1)
        val middle = createdTestModel("middle", 2)
        val leaf = createdTestModel("leaf", 3)
        val leafUow = uow<Any> { add(leaf) }
        val middleUow = uow<Any> {
            add(middle)
            execute(leafUow, TestPrincipal) { DummyUow.Params }
        }
        perform {
            add(own)
            execute(middleUow, TestPrincipal) { DummyUow.Params }
        }.persisted() shouldBe
            listOf(AddModel::class to own, AddModel::class to middle, AddModel::class to leaf)
    }

    test("A child that composes a stubbed child is merged into its parent") {
        val own = createdTestModel("parent", 1)
        val claimed = existingCreatedTestModel(param1 = "claimed", param2 = 2)
        val stub = { exCtx: ExecutionContext ->
            object : DummyUow<Any>(exCtx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = stubChanges<Any>(claimed)
            }
        }
        perform {
            add(own)
            execute(uow<Any> { execute(stub, TestPrincipal) { DummyUow.Params } }, TestPrincipal) { DummyUow.Params }
        }.persisted() shouldBe listOf(AddModel::class to own, NoopModel::class to claimed)
    }

    test("A child that deletes an entity is merged into its parent") {
        val tag = Tag.priorityTag(randomDepartmentId().id, 1)
        perform {
            execute(uow<Any> { delete(tag) }, TestPrincipal) { DummyUow.Params }
        }.entityChangesToPersist shouldBe listOf(DeleteEntity(tag))
    }

    context("An aggregate a child added, and the new model it owns") {
        val hire = newHire()
        val dept = newDeptAggregate(name = "Eng", boss = bossId, ration = BUBALEH, employees = listOf(hire))
        val moved = mutating { hire.changeDepartment(elsewhere("O")) }
        val addDept = uow<Any> { add(dept) }

        test("The parent cannot update the owned model on its own, as in a single block") {
            listOf<suspend ChangesDsl.() -> Unit>(
                { update(hire) { changeDepartment(elsewhere("O")) } },
                { update(moved) },
            ).forEach { register ->
                val ex = shouldThrow<IllegalArgumentException> {
                    perform {
                        execute(addDept, TestPrincipal) { DummyUow.Params }
                        register()
                    }
                }
                ex.message shouldBe "Attempted to register new model [${hire.id().stringValue()}] as changed"
            }
        }

        test("The parent cannot claim the owned model unchanged, as in a single block") {
            val ex = shouldThrow<IllegalArgumentException> {
                perform {
                    execute(addDept, TestPrincipal) { DummyUow.Params }
                    notChanged(hire)
                }
            }
            ex.message shouldBe "Attempted to register new model [${hire.id().stringValue()}] as unchanged"
        }

        test("The parent changes the owned model through its aggregate") {
            val deptOwningMoved = DeptAggregate(
                dept.id(), "Renamed", bossId, 1, BUBALEH, listOf(moved),
                dept.raise(NameChanged(dept.id(), dept.name, "Renamed")),
            )
            perform {
                execute(addDept, TestPrincipal) { DummyUow.Params }
                update(deptOwningMoved)
            }.persisted() shouldBe listOf(AddModel::class to deptOwningMoved, AddModel::class to moved)
        }
    }

    test("An owned model the aggregate no longer owns is not persisted, whichever block registered the aggregate") {
        val hire = newHire()
        val dept = newDeptAggregate(name = "Eng", boss = bossId, ration = BUBALEH, employees = listOf(hire))
        val deptWithoutHire = DeptAggregate(
            dept.id(), "Renamed", bossId, 1, BUBALEH, listOf<Employee>(),
            dept.raise(NameChanged(dept.id(), dept.name, "Renamed")),
        )
        val unrelated = existingCreatedTestModel(param1 = "x", param2 = 1).changeParam1("y")
        val childAdds = perform {
            execute(uow<Any> { add(dept) }, TestPrincipal) { DummyUow.Params }
            update(deptWithoutHire)
        }
        val unrelatedChildFirst = perform {
            add(dept)
            execute(uow<Any> { update(unrelated) }, TestPrincipal) { DummyUow.Params }
            execute(uow<Any> { update(deptWithoutHire) }, TestPrincipal) { DummyUow.Params }
        }
        val childUpdates = perform {
            add(dept)
            execute(uow<Any> { update(deptWithoutHire) }, TestPrincipal) { DummyUow.Params }
        }
        childAdds.persisted() shouldBe listOf(AddModel::class to deptWithoutHire)
        unrelatedChildFirst.persisted() shouldBe
            listOf(AddModel::class to deptWithoutHire, UpdateModel::class to unrelated)
        childUpdates.persisted() shouldBe listOf(AddModel::class to deptWithoutHire)
    }

    test("A parent's aggregate keeps its owned models with it after an unrelated child ran") {
        val hire = newHire()
        val dept = newDeptAggregate(name = "Eng", boss = bossId, ration = BUBALEH, employees = listOf(hire))
        val moved = mutating { hire.changeDepartment(elsewhere("O")) }
        val deptOwningMoved = DeptAggregate(
            dept.id(), "Renamed", bossId, 1, BUBALEH, listOf(moved),
            dept.raise(NameChanged(dept.id(), dept.name, "Renamed")),
        )
        val unrelated = existingCreatedTestModel(param1 = "x", param2 = 1).changeParam1("y")
        perform {
            add(dept)
            execute(uow<Any> { update(unrelated) }, TestPrincipal) { DummyUow.Params }
            update(deptOwningMoved)
        }.persisted() shouldBe listOf(
            AddModel::class to deptOwningMoved,
            AddModel::class to moved,
            UpdateModel::class to unrelated,
        )
    }

    test("A parent cannot claim unchanged a stale read of a model a child's aggregate changed") {
        val employee = storedEmployee()
        val moved = mutating { employee.changeDepartment(elsewhere("O")) }
        val stored = storedDept(employee)
        val owningMoved = DeptAggregate(
            stored.id(), "D1", bossId, 1, BUBALEH, listOf(moved), stored.raise(NameChanged(stored.id(), "D0", "D1")),
        )
        val ex = shouldThrow<IllegalStateException> {
            perform {
                execute(uow<Any> { update(owningMoved) }, TestPrincipal) { DummyUow.Params }
                notChanged(employee)
            }
        }
        ex.message shouldBe "Model [${employee.id().stringValue()}] is registered as unchanged, but aggregate " +
            "[${stored.id().stringValue()}] owns a changed instance of it: the write would be silently dropped"
    }

    context("Order of the persisted changes") {
        test("A model a child added, then an aggregate the parent added that owns it, persist as in one block") {
            val hire = newHire()
            val unrelated = existingCreatedTestModel(param1 = "x", param2 = 1).changeParam1("y")
            val dept = newDeptAggregate(name = "Eng", boss = bossId, ration = BUBALEH, employees = listOf(hire))
            val composed = perform {
                execute(uow<Any> { add(hire); update(unrelated) }, TestPrincipal) { DummyUow.Params }
                add(dept)
            }
            val single = perform {
                add(hire)
                update(unrelated)
                add(dept)
            }
            composed.persisted() shouldBe
                listOf(AddModel::class to hire, UpdateModel::class to unrelated, AddModel::class to dept)
            composed.persisted() shouldBe single.persisted()
        }

        test("An owned model the parent registers after the child persists where the parent registered it") {
            val employee = storedEmployee()
            val moved = mutating { employee.changeDepartment(elsewhere("O")) }
            val stored = storedDept(employee)
            val owningMoved = DeptAggregate(
                stored.id(), "D1", bossId, 1, BUBALEH, listOf(moved),
                stored.raise(NameChanged(stored.id(), "D0", "D1")),
            )
            val unrelated = existingCreatedTestModel(param1 = "x", param2 = 1).changeParam1("y")
            val changes = perform {
                execute(uow<Any> { update(owningMoved); update(unrelated) }, TestPrincipal) { DummyUow.Params }
                update(moved) { changeDepartment(elsewhere("P")) }
            }
            changes.modelChangesToPersist.map { it.id } shouldBe listOf(stored.id(), unrelated.id(), employee.id())
            changes.modelChangesToPersist.last().modelEvents.size shouldBe 2
        }

        test("An owned model that moves to a new aggregate persists after that aggregate") {
            val employee = storedEmployee()
            val moved = mutating { employee.changeDepartment(elsewhere("O")) }
            val first = DeptAggregate(
                randomDepartmentId(), "A", bossId, 1, BUBALEH, listOf(moved), persistentState(V1, null),
            ).rename("A1")
            val firstWithout = DeptAggregate(
                first.id(), "A2", bossId, 1, BUBALEH, listOf<Employee>(),
                first.raise(NameChanged(first.id(), "A1", "A2")),
            )
            val further = mutating { moved.changeDepartment(elsewhere("Q")) }
            val second = newDeptAggregate(name = "B", boss = bossId, ration = BUBALEH, employees = listOf(further))
            perform {
                execute(uow<Any> { update(first) }, TestPrincipal) { DummyUow.Params }
                update(firstWithout)
                add(second)
            }.persisted() shouldBe listOf(
                UpdateModel::class to firstWithout,
                AddModel::class to second,
                UpdateModel::class to further,
            )
        }
    }
})
