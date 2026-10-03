package com.razz.eva.uow.composable

import com.razz.eva.domain.Model
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.DeptAggregate
import com.razz.eva.domain.DeptAggregate.Companion.newDeptAggregate
import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.domain.mutating
import com.razz.eva.uow.AddModel
import com.razz.eva.uow.ModelChange
import com.razz.eva.uow.UpdateModel
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.CapturingSlot
import io.mockk.slot
import com.razz.eva.domain.TestModel
import com.razz.eva.domain.TestModel.CreatedTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.events.UowEvent
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UnitOfWorkExecutor
import com.razz.eva.uow.stubChanges
import com.razz.eva.uow.TestDoubleApi
import com.razz.eva.uow.verify.verifyInOrder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
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

    fun persistingReturning(vararg flushed: Model<*, *>): Persisting {
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

    // a Persisting that records the model changes the executor hands it
    fun capturing(): Pair<Persisting, CapturingSlot<List<ModelChange>>> {
        val persisting = mockk<Persisting>(relaxed = true)
        val persisted = slot<List<ModelChange>>()
        coEvery {
            persisting.persist(
                uowName = any(),
                params = DummyUow.Params,
                principal = TestPrincipal,
                modelChanges = capture(persisted),
                entityChanges = any(),
                now = any(),
                uowSupportsOutOfOrderPersisting = any(),
                connectionMode = any(),
            )
        } returns Pair(uowEvent(), listOf())
        return persisting to persisted
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

    test("A dirty ancestor of the registered instance passes and is replaced by the persisted state") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val flushed = existingCreatedTestModel(id = read.id(), param1 = "flushed", param2 = 2)
        val first = read.changeParam1("first")
        val result = executor(persistingReturning(flushed)).execute(
            TestPrincipal,
            uow<TestModel> {
                update(first.changeParam2(2))
                first
            },
        ) { DummyUow.Params }
        result shouldBeSameInstanceAs flushed
    }

    test("A dirty roundtrip seed extended by a composed child resolves to the persisted state") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val flushed = existingCreatedTestModel(id = read.id(), param1 = "flushed", param2 = 2)
        val result = executor(persistingReturning(flushed)).execute(
            TestPrincipal,
            uow {
                val first = update(read.changeParam1("parent"))
                val seed = roundtrip { p -> Pair(p<TestModel>(first), "label") }
                execute(uow<CreatedTestModel> { update(first.changeParam2(2)) }, TestPrincipal) { DummyUow.Params }
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

    test("An unregistered changed model inside a triple or an array fails") {
        val registered = existingCreatedTestModel(param1 = "registered", param2 = 1)
        val stray = existingCreatedTestModel(param1 = "stray", param2 = 2)
        listOf<suspend ChangesDsl.() -> Any>(
            { Triple(1, 2, stray.changeParam2(3)) },
            { arrayOf<Any>(stray.changeParam2(4)) },
        ).forEach { tail ->
            val ex = shouldThrow<IllegalStateException> {
                executor(persistingReturning()).execute(
                    TestPrincipal,
                    uow {
                        update(registered.changeParam1("mutated"))
                        tail()
                    },
                ) { DummyUow.Params }
            }
            checkNotNull(ex.message) shouldContain "Unregistered changed model [${stray.id().stringValue()}]"
        }
    }

    test("A result that contains itself is walked once") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val looped = mutableListOf<Any>()
        looped.add(looped)
        val result = executor(persistingReturning()).execute(
            TestPrincipal,
            uow<Any> {
                update(read.changeParam1("mutated"))
                looped
            },
        ) { DummyUow.Params }
        result shouldBeSameInstanceAs looped
    }

    test("noChanges in a composed child accepts an ancestor of the parent's registration") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val first = read.changeParam1("first")
        val child = { exCtx: ExecutionContext ->
            object : DummyUow<TestModel>(exCtx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = noChanges<TestModel>(first)
            }
        }
        executor(persistingReturning(read)).execute(
            TestPrincipal,
            uow<TestModel> {
                update(first.changeParam2(2))
                execute(child, TestPrincipal) { DummyUow.Params }
            },
        ) { DummyUow.Params }
    }

    test("noChanges in a composed child refuses a sibling of the parent's registration") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val child = { exCtx: ExecutionContext ->
            object : DummyUow<TestModel>(exCtx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) =
                    noChanges<TestModel>(read.changeParam1("sibling"))
            }
        }
        val ex = shouldThrow<IllegalStateException> {
            executor(persistingReturning()).execute(
                TestPrincipal,
                uow<TestModel> {
                    update(read.changeParam1("registered"))
                    execute(child, TestPrincipal) { DummyUow.Params }
                },
            ) { DummyUow.Params }
        }
        checkNotNull(ex.message) shouldContain "to noChanges: the write would be silently dropped"
    }

    test("verifyInOrder fails a spec whose result carries a write its registration lacks") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val uow = uow<TestModel> {
            update(read.changeParam1("registered"))
            read.changeParam1("returned")
        }(ExecutionContext(fixedUTC(ofEpochMilli(0)), OpenTelemetry.noop()))
        val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)
        val ex = shouldThrow<IllegalStateException> {
            changes verifyInOrder {
                updates<CreatedTestModel> { }
                emits<com.razz.eva.domain.TestModelEvent.TestModelEvent1> { }
            }
        }
        checkNotNull(ex.message) shouldContain "carries a write its registered instance does not"
    }

    test("A composed child declining on a model its parent registered leaves the parent's change standing") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val first = read.changeParam1("parent")
        val (persisting, persisted) = capturing()
        executor(persisting).execute(
            TestPrincipal,
            uow<TestModel> {
                update(first)
                execute(uow<TestModel> { update(first) { this } }, TestPrincipal) { DummyUow.Params }
            },
        ) { DummyUow.Params }
        persisted.captured.single().model shouldBeSameInstanceAs first
    }

    test("A composed child declining on a model its parent added leaves it added") {
        val created = createdTestModel("new", 1)
        val (persisting, persisted) = capturing()
        executor(persisting).execute(
            TestPrincipal,
            uow<TestModel> {
                add(created)
                execute(uow<TestModel> { update(created) { this } }, TestPrincipal) { DummyUow.Params }
            },
        ) { DummyUow.Params }
        persisted.captured.single().shouldBeInstanceOf<AddModel<*, *, *>>().model shouldBeSameInstanceAs created
    }

    test("A composed child declining on its own mutation of a model its parent claimed unchanged registers it") {
        val read = existingCreatedTestModel(param1 = "read", param2 = 1)
        val dirtied = read.changeParam1("child")
        val (persisting, persisted) = capturing()
        executor(persisting).execute(
            TestPrincipal,
            uow<TestModel> {
                notChanged(read)
                execute(uow<TestModel> { update(dirtied) { this } }, TestPrincipal) { DummyUow.Params }
            },
        ) { DummyUow.Params }
        persisted.captured.single().shouldBeInstanceOf<UpdateModel<*, *, *>>().model shouldBeSameInstanceAs dirtied
    }

    test("A composed child declining on its own unwitnessed extension of a model its parent added merges it") {
        val created = createdTestModel("new", 1)
        val extended = created.changeParam1("child")
        val (persisting, persisted) = capturing()
        executor(persisting).execute(
            TestPrincipal,
            uow<TestModel> {
                add(created)
                execute(uow<TestModel> { update(extended) { this } }, TestPrincipal) { DummyUow.Params }
            },
        ) { DummyUow.Params }
        val change = persisted.captured.single().shouldBeInstanceOf<AddModel<*, *, *>>()
        change.model shouldBeSameInstanceAs extended
        change.modelEvents shouldHaveSize 2
    }

    test("A composed child declining on an employee its parent's new aggregate owns adds it, once") {
        val bossId = EmployeeId()
        val hire = mutating { newEmployee(Name("Kim", "Day"), randomDepartmentId(), "kim@test.com", BUBALEH) }
        val dept = newDeptAggregate(name = "Engineering", boss = bossId, ration = BUBALEH, employees = listOf(hire))
        val (persisting, persisted) = capturing()
        executor(persisting).execute(
            TestPrincipal,
            uow<DeptAggregate<List<Employee>>> {
                add(dept)
                execute(uow<Employee> { update(hire) { this } }, TestPrincipal) { DummyUow.Params }
                dept
            },
        ) { DummyUow.Params }
        persisted.captured.map { it.model } shouldBe listOf(dept, hire)
    }

    test("A declined employee an aggregate owns is registered on its own and survives the aggregate dropping it") {
        val bossId = EmployeeId()
        val employee = Employee(
            EmployeeId(), Name("Lou", "Reed"), randomDepartmentId(), "lou@test.com", BUBALEH, persistentState(V1, null),
        )
        val otherDept = OwnedDepartment(randomDepartmentId(), "Other", bossId, 1, BUBALEH, persistentState(V1, null))
        val moved = mutating { employee.changeDepartment(otherDept) }
        val stored = DeptAggregate(
            randomDepartmentId(), "D0", bossId, 1, BUBALEH, listOf(employee), persistentState(V1, null),
        )
        val owning = DeptAggregate(
            stored.id(), "D1", bossId, 1, BUBALEH, listOf(moved),
            stored.raise(DepartmentEvent.NameChanged(stored.id(), "D0", "D1")),
        )
        val emptied = DeptAggregate(
            owning.id(), "D2", bossId, 1, BUBALEH, listOf(),
            owning.raise(DepartmentEvent.NameChanged(owning.id(), "D1", "D2")),
        )
        val (persisting, persisted) = capturing()
        executor(persisting).execute(
            TestPrincipal,
            uow<DeptAggregate<List<Employee>>> {
                update(owning)
                execute(
                    uow<DeptAggregate<List<Employee>>> {
                        update(moved) { this }
                        update(emptied)
                    },
                    TestPrincipal,
                ) { DummyUow.Params }
            },
        ) { DummyUow.Params }
        persisted.captured.single { it.id == employee.id() }.model shouldBeSameInstanceAs moved
    }

    test("A new employee a child declines on and then drops from its new aggregate is still inserted") {
        val bossId = EmployeeId()
        val hire = mutating { newEmployee(Name("Max", "Born"), randomDepartmentId(), "max@test.com", BUBALEH) }
        val dept = newDeptAggregate(name = "Engineering", boss = bossId, ration = BUBALEH, employees = listOf(hire))
        val dropped = DeptAggregate(
            dept.id(), "Renamed", bossId, 1, BUBALEH, listOf(),
            dept.raise(DepartmentEvent.NameChanged(dept.id(), dept.name, "Renamed")),
        )
        val (persisting, persisted) = capturing()
        executor(persisting).execute(
            TestPrincipal,
            uow<DeptAggregate<List<Employee>>> {
                add(dept)
                execute(
                    uow<DeptAggregate<List<Employee>>> {
                        update(hire) { this }
                        update(dropped)
                    },
                    TestPrincipal,
                ) { DummyUow.Params }
            },
        ) { DummyUow.Params }
        persisted.captured.single { it.id == hire.id() }.shouldBeInstanceOf<AddModel<*, *, *>>()
            .model shouldBeSameInstanceAs hire
    }

    test("Declining on a stale clean read of an employee an aggregate in the block moved leaves the move") {
        val bossId = EmployeeId()
        val employee = Employee(
            EmployeeId(), Name("Ned", "Kelly"), randomDepartmentId(), "ned@test.com", BUBALEH,
            persistentState(V1, null),
        )
        val otherDept = OwnedDepartment(randomDepartmentId(), "Other", bossId, 1, BUBALEH, persistentState(V1, null))
        val moved = mutating { employee.changeDepartment(otherDept) }
        val stored = DeptAggregate(
            randomDepartmentId(), "D0", bossId, 1, BUBALEH, listOf(employee), persistentState(V1, null),
        )
        val owning = DeptAggregate(
            stored.id(), "D1", bossId, 1, BUBALEH, listOf(moved),
            stored.raise(DepartmentEvent.NameChanged(stored.id(), "D0", "D1")),
        )
        val (persisting, persisted) = capturing()
        executor(persisting).execute(
            TestPrincipal,
            uow<Employee> {
                update(owning)
                update(employee) { this }
            },
        ) { DummyUow.Params }
        persisted.captured.single { it.id == employee.id() }.model shouldBeSameInstanceAs moved
    }

    test("A parent spec over a stubbed child's new model sees it added, as production inserts it") {
        val created = createdTestModel("stubbed", 1)
        @OptIn(TestDoubleApi::class)
        val stubbed = { exCtx: ExecutionContext ->
            object : DummyUow<TestModel>(exCtx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) =
                    stubChanges<TestModel>(created)
            }
        }
        val parent = uow<TestModel> {
            val fromChild = execute(stubbed, TestPrincipal) { DummyUow.Params }
            update((fromChild as CreatedTestModel).changeParam1("extended"))
        }(ExecutionContext(fixedUTC(ofEpochMilli(0)), OpenTelemetry.noop()))
        val changes = parent.tryPerform(TestPrincipal, DummyUow.Params)
        changes verifyInOrder {
            adds<CreatedTestModel> { param1 shouldBe "extended" }
            emits<com.razz.eva.domain.TestModelEvent.TestModelCreated> { }
            emits<com.razz.eva.domain.TestModelEvent.TestModelEvent1> { }
        }
    }

    test("update(model) { } on a new model that comes back unchanged names add") {
        val ex = shouldThrow<IllegalStateException> {
            executor(persistingReturning()).execute(
                TestPrincipal,
                uow<TestModel> { update(createdTestModel("new", 1)) { this } },
            ) { DummyUow.Params }
        }
        checkNotNull(ex.message) shouldContain "register a new model with add"
    }

    test("A new employee owned by an unregistered aggregate in the result fails") {
        val bossId = EmployeeId()
        val hire = mutating { newEmployee(Name("Ida", "Wells"), randomDepartmentId(), "ida@test.com", BUBALEH) }
        val dept = DeptAggregate(
            randomDepartmentId(), "Unregistered", bossId, 1, BUBALEH, listOf(hire), persistentState(V1, null),
        )
        val other = existingCreatedTestModel(param1 = "other", param2 = 1)
        val ex = shouldThrow<IllegalStateException> {
            executor(persistingReturning()).execute(
                TestPrincipal,
                uow {
                    update(other.changeParam1("registered"))
                    dept
                },
            ) { DummyUow.Params }
        }
        checkNotNull(ex.message) shouldContain "Unregistered new model [${hire.id().stringValue()}]"
    }

    test("A parent that moves an employee owned by an aggregate its child added persists the move") {
        val bossId = EmployeeId()
        val hire = mutating { newEmployee(Name("Jo", "March"), randomDepartmentId(), "jo@test.com", BUBALEH) }
        val dept = newDeptAggregate(name = "Engineering", boss = bossId, ration = BUBALEH, employees = listOf(hire))
        val otherDept = OwnedDepartment(randomDepartmentId(), "Other", bossId, 1, BUBALEH, persistentState(V1, null))
        val moved = mutating { hire.changeDepartment(otherDept) }
        val renamed = DeptAggregate(
            dept.id(), "Renamed", bossId, 1, BUBALEH, listOf(moved),
            dept.raise(DepartmentEvent.NameChanged(dept.id(), dept.name, "Renamed")),
        )
        val (persisting, persisted) = capturing()
        executor(persisting).execute(
            TestPrincipal,
            uow<DeptAggregate<List<Employee>>> {
                execute(uow<DeptAggregate<List<Employee>>> { add(dept) }, TestPrincipal) { DummyUow.Params }
                update(renamed)
            },
        ) { DummyUow.Params }
        persisted.captured.single { it.id == hire.id() }.model shouldBeSameInstanceAs moved
        persisted.captured.single { it.id == dept.id() }.model shouldBeSameInstanceAs renamed
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
