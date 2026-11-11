@file:OptIn(com.razz.eva.uow.TestDoubleApi::class)

package com.razz.eva.uow.guarded

import com.razz.eva.domain.mutating
import com.razz.eva.domain.Department
import com.razz.eva.domain.Department.OrphanedDepartment
import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent.NameChanged
import com.razz.eva.domain.DeptAggregate
import com.razz.eva.domain.DeptAggregate.Companion.newDeptAggregate
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeEvent.DepartmentChanged
import com.razz.eva.domain.EmployeeId.Companion.randomEmployeeId
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Tag
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.domain.Version.Companion.version
import com.razz.eva.domain.addEmployee
import com.razz.eva.persistence.PersistenceException.StaleRecordException
import com.razz.eva.test.uow.afterRetry
import com.razz.eva.test.uow.forAttempt
import com.razz.eva.test.uow.initial
import com.razz.eva.uow.AddEntity
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.stubChanges
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UpdateModel
import com.razz.eva.uow.composable.ChangesDsl
import com.razz.eva.uow.composable.UnitOfWork
import com.razz.eva.uow.guarded.Fixtures.department
import com.razz.eva.uow.guarded.Fixtures.employee
import com.razz.eva.uow.guarded.Fixtures.proof
import com.razz.eva.uow.guarded.MoveEmployeeUow.How.DECOY
import com.razz.eva.uow.guarded.MoveEmployeeUow.How.PROVEN
import com.razz.eva.uow.guarded.MoveEmployeeUow.How.UNREAD
import com.razz.eva.uow.guarded.MoveEmployeeUow.Params
import com.razz.eva.uow.guarded.Proven.Companion.proven
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.opentelemetry.api.OpenTelemetry
import kotlin.reflect.KClass

class GuardedUnitOfWorkSpec : FunSpec({

    val noAttempt = ExecutionContext(fixedUTC(millisUTC().instant()), OpenTelemetry.noop())
    val home = department("Home")
    val away = department("Away")
    val elsewhere = department("Elsewhere")
    val employee = employee(department = home.id())
    val moved = employee(id = employee.id(), department = elsewhere.id())
    val first = initial(TestPrincipal)
    val retry = afterRetry(first)
    // A unit of work in this spec is built for the attempt of first, as the executor would build it, unless the
    // test builds it with retryContext.
    val executionContext = noAttempt.forAttempt(first)
    val retryContext = noAttempt.forAttempt(retry)
    fun uow(unguarded: Set<KClass<out Model<*, *>>> = setOf(), context: ExecutionContext = executionContext) =
        MoveEmployeeUow(context, unguarded, away)
    fun InstantiationContext.moving(proof: TestProof<Employee>, reloaded: Employee = moved) =
        proven(proof) { reloaded }
    fun InstantiationContext.params(proof: TestProof<Employee>) = EmployeeParams(moving(proof))
    fun <R : Any> composing(child: (ExecutionContext) -> UnitOfWork<TestPrincipal, ChildParams, R>) =
        ComposingGuardedUow(executionContext, away, child, swallowing = false)
    fun <R : Any> targetFirst(child: (ExecutionContext, Employee) -> UnitOfWork<TestPrincipal, ChildParams, R>) =
        TargetFirstGuardedUow(executionContext, away, child)
    fun refusedWrite(parent: String, child: String, id: String) = "$parent refuses composed unit of work [$child]: " +
        "it registered [$id], models that need a proof. A child may not add, update or keep one with notChanged: " +
        "write it in $parent, through a Proven field"
    fun ungated(uow: String, id: String, type: String) = "$uow may not update $id: the block did not read it from " +
        "a Proven with model(), and $type is not in unguardedWrites. Read it with params.x.model()"
    fun unlisted(uow: String, id: String, type: String) = "$uow may not update $id: the block did not read it from " +
        "a Proven with model(), and $type is not in unguardedWrites. Read it with params.x.model(), or list its " +
        "type in unguardedWrites"
    fun unwritten(uow: String, ids: List<Any>) = "$uow read ${ids.map { it.toString() }} with model() and " +
        "registered no change for them. Call update(model) on each, or notChanged(model) to keep it as it is"
    fun ownedUnread(owner: String, owned: String) = "Block registers $owner, which owns the changed model $owned, " +
        "and the block did not read that model with model(). Read it through its Proven field, and put the model " +
        "that model() returned into the aggregate"
    fun boundElsewhere(id: String) = "MoveEmployeeUow cannot read the Proven for $id: its params were built in " +
        "another attempt or another run. Build them in the params lambda of the execute call that runs this unit of " +
        "work. In a spec, build the unit of work with executionContext.forAttempt(attempt), with the context that " +
        "built the params"
    fun attemptless(uow: String, id: String) = "$uow cannot read the Proven for $id: it was built with an " +
        "ExecutionContext that carries no attempt. Build the unit of work in the factory of the execute call, with " +
        "the ExecutionContext the factory receives. A composed child reads no Proven. In a spec, build it with " +
        "executionContext.forAttempt(attempt)"
    fun stale(uow: String, id: String) =
        "$uow registers $id at version 2, and model() returned it at version 1. Change the model that model() returned"
    fun used() = "This proof for test.write was already used to run a unit of work. One proof runs one unit of work: " +
        "get a new proof for another run"
    fun notFirst() = "The proof for test.write was not wrapped with proven(...) in the first attempt of this run. " +
        "Wrap the same proofs in every attempt. In a spec, build the first attempt's params before the retry's"
    fun storedDept(employees: List<Employee>) =
        DeptAggregate(home.id(), "Stored", randomEmployeeId(), 1, BUBALEH, employees, persistentState(V1, null))
    fun stubbedChild(name: String, vararg claims: Model<*, *>) = { ctx: ExecutionContext ->
        object : UnitOfWork<TestPrincipal, ChildParams, String>(ctx) {
            override fun name() = name
            override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) =
                stubChanges("stubbed", *claims)
        }
    }
    fun plainChild(name: String, body: suspend ChangesDsl.() -> String) = { ctx: ExecutionContext ->
        object : UnitOfWork<TestPrincipal, ChildParams, String>(ctx) {
            override fun name() = name
            override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) = changes(body)
        }
    }
    fun block(
        unguarded: Set<KClass<out Model<*, *>>> = setOf(),
        guarding: Guarding = EmployeesGuarded,
        body: suspend GuardedChangesDsl<TestProof<*>>.(EmployeeParams) -> Employee,
    ) = object : GuardedUnitOfWork<TestPrincipal, TestProof<*>, EmployeeParams, Employee>(
        executionContext, Configuration.default(), guarding, unguarded,
    ) {
        override fun name() = "Block"
        override suspend fun tryPerform(principal: TestPrincipal, params: EmployeeParams) =
            changes { body(params) }
    }

    test("writes a proven model") {
        val changes = uow().tryPerform(TestPrincipal, first.run { Params(moving(proof(employee)), "moves") })
        val change = changes.modelChangesToPersist.single().shouldBeInstanceOf<UpdateModel<*, *>>()
        change.model.shouldBeInstanceOf<Employee>().departmentId shouldBe away.id()
        change.modelEvents shouldBe listOf(DepartmentChanged(employee.id(), home.id(), away.id()))
        changes.result shouldBeSameInstanceAs change.model
    }

    test("uses the proof's model on the first attempt and never reloads it") {
        val never: Employee by lazy { error("the first attempt does not reload") }
        val proof = proof(employee)
        val changes = uow().tryPerform(TestPrincipal, first.run { Params(proven(proof) { never }, "moves") })
        changes.result.id() shouldBe employee.id()
        proof.checkedAgain shouldBe listOf()
    }

    test("reloads a proven model on a retry and asks the proof again about the reloaded model") {
        val proof = proof(employee)
        first.moving(proof)
        val params = retry.run { Params(moving(proof, moved), "moves") }
        val changes = uow(context = retryContext).tryPerform(TestPrincipal, params)
        proof.checkedAgain shouldBe listOf(moved)
        val change = changes.modelChangesToPersist.single().shouldBeInstanceOf<UpdateModel<*, *>>()
        change.modelEvents shouldBe listOf(DepartmentChanged(employee.id(), elsewhere.id(), away.id()))
    }

    test("a retry whose reload the proof refuses writes nothing") {
        val proof = proof(employee, refused = setOf(elsewhere))
        first.moving(proof)
        val params = retry.run { Params(moving(proof), "moves") }
        shouldThrow<Refused> { uow(context = retryContext).tryPerform(TestPrincipal, params) }
            .message shouldBe "moved out of reach"
    }

    test("a reload that answers another model is refused before the proof is asked") {
        val proof = proof(employee)
        val other = employee()
        first.moving(proof)
        val params = retry.run { Params(moving(proof, other), "moves") }
        shouldThrow<IllegalStateException> {
            uow(context = retryContext).tryPerform(TestPrincipal, params)
        }.message shouldBe "The reload of a Proven returned ${other.id().stringValue()}, and its proof is for " +
            "${employee.id().stringValue()}. The reload must load the proof's target by its id"
        proof.checkedAgain shouldBe listOf()
    }

    test("refuses on a retry a Proven the first attempt built and did not read, and reloads nothing") {
        val proof = proof(employee)
        val kept = first.run { Params(moving(proof), "moves") }
        shouldThrow<IllegalStateException> { uow(context = retryContext).tryPerform(TestPrincipal, kept) }
            .message shouldBe boundElsewhere(employee.id().stringValue())
        proof.checkedAgain shouldBe listOf()
    }

    test("one proof runs one unit of work: a second first attempt with it is refused, a retry is not") {
        val proof = proof(employee)
        first.moving(proof)
        retry.moving(proof)
        shouldThrow<IllegalStateException> { first.moving(proof) }
            .message shouldBe used()
    }

    test("a retry accepts only a proof the first attempt of the same run used") {
        val unspent = proof(employee)
        shouldThrow<IllegalStateException> { retry.moving(unspent) }
            .message shouldBe notFirst()
        val spent = proof(employee)
        initial(TestPrincipal).moving(spent)
        shouldThrow<IllegalStateException> { retry.moving(spent) }
            .message shouldBe notFirst()
        first.moving(unspent)
    }

    test("refuses to wrap a proof issued for another principal, and leaves it unused") {
        val proof = TestProof(OtherPrincipal, employee) { }
        shouldThrow<IllegalStateException> { first.moving(proof) }
            .message shouldBe "The proof for test.write on ${employee.id().stringValue()} was issued for another " +
            "principal than the one this unit of work runs under. Run it under the proof's principal"
        initial(OtherPrincipal).moving(proof)
    }

    test("refuses to wrap a proof in a composed child's params, and leaves it unused") {
        val proof = proof(employee)
        val parent = block { params ->
            execute({ RenameDepartmentUow(it, home) }, TestPrincipal) {
                moving(proof)
                ChildParams
            }
            update(params.employee.model()) { changeDepartment(away) }
        }
        shouldThrow<IllegalStateException> { parent.tryPerform(TestPrincipal, first.params(proof(employee))) }
            .message shouldBe "proven(...) was called in the params of a composed child. A child takes no proof: " +
            "call proven(...) in the params lambda of the unit of work the caller runs, and write the guarded model " +
            "there"
        first.moving(proof)
    }

    test("refuses to wrap a proof on a model that is not stored") {
        val hired = mutating { newEmployee(Name("Eve", "Green"), home.id(), "eve@test.com", BUBALEH) }
        shouldThrow<IllegalArgumentException> { first.run { proven(TestProof(TestPrincipal, hired) { }) { hired } } }
            .message shouldBe "The proof for test.write is on ${hired.id().stringValue()}, which is new or has " +
            "unsaved changes. Get the proof on the model as it was loaded"
    }

    test("refuses a second block that reads a Proven of params a first block already read") {
        val params = first.run { Params(moving(proof(employee)), "moves") }
        uow().tryPerform(TestPrincipal, params)
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe "MoveEmployeeUow: another block already read the Proven for " +
            "${employee.id().stringValue()}. Build the params in the params lambda for each attempt, and do not keep " +
            "a Proven between attempts or runs"
    }

    test("refuses a block that reads a proven model and writes nothing on it") {
        val params = first.run { Params(moving(proof(employee)), "reads", skips = true) }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe unwritten("MoveEmployeeUow", listOf(employee.id().stringValue()))
    }

    test("notChanged on a proven model registers a version check and no write") {
        val params = first.run { Params(moving(proof(employee)), "holds", holds = true) }
        uow().tryPerform(TestPrincipal, params).modelChangesToPersist.single() shouldBe NoopModel(employee)
    }

    test("refuses a block that adds a new model and only reads its proven model") {
        val added = Department.newDepartment("New", employee.id(), 1, BUBALEH)
        val params = first.run { Params(moving(proof(employee)), "adds", adds = added, skips = true) }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe unwritten("MoveEmployeeUow", listOf(employee.id().stringValue()))
    }

    test("refuses a proven model updated at a version other than the one model() returned") {
        val stale = Employee(
            employee.id(), employee.name, home.id(), employee.email, BUBALEH, persistentState(version(2), null),
        )
        val params = first.run { Params(moving(proof(employee)), "stale", stale = stale) }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe stale("MoveEmployeeUow", employee.id().stringValue())
    }

    test("refuses notChanged on a proven model at a version other than the one model() returned") {
        val stale = Employee(
            employee.id(), employee.name, home.id(), employee.email, BUBALEH, persistentState(version(2), null),
        )
        val params = first.run { Params(moving(proof(employee)), "holds stale", holds = true, stale = stale) }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe stale("MoveEmployeeUow", employee.id().stringValue())
    }

    test("refuses two Proven fields for one model in one block") {
        val params = first.run {
            Params(moving(proof(employee)), "twice", extra = moving(proof(employee)), how = PROVEN)
        }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe "MoveEmployeeUow: two Proven fields carry ${employee.id().stringValue()}. A block " +
            "reads a model through one proof only: pass it once"
    }

    test("refuses an update beside the proven model, though the block updates the proven model too") {
        val beside = employee(department = home.id())
        val params = first.run { Params(moving(proof(employee)), "moves two", beside = beside) }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe ungated("MoveEmployeeUow", beside.id().stringValue(), "Employee")
    }

    test("offers unguardedWrites in the refusal only for a type that cannot be guarded") {
        val dept = storedDept(listOf())
        shouldThrow<IllegalStateException> {
            block { params ->
                update(dept.rename("Renamed"))
                update(params.employee.model()) { changeDepartment(away) }
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe unlisted("Block", dept.id().stringValue(), "DeptAggregate")
    }

    test("writes two models, each read through its own proof") {
        val extra = employee(department = home.id())
        val params = first.run {
            Params(moving(proof(employee)), "moves two", extra = proven(proof(extra)) { error("first") })
        }
        val changes = uow().tryPerform(TestPrincipal, params)
        changes.modelChangesToPersist.map { it.id } shouldContainExactly listOf(extra.id(), employee.id())
    }

    test("asks the proof of a second proven model again on a retry") {
        val extra = employee(department = home.id())
        val extraMoved = employee(id = extra.id(), department = elsewhere.id())
        val extraProof = proof(extra)
        val employeeProof = proof(employee)
        first.run { Params(moving(employeeProof), "first", extra = proven(extraProof) { error("first") }) }
        val params = retry.run {
            Params(moving(employeeProof), "moves two", extra = proven(extraProof) { extraMoved })
        }
        uow(context = retryContext).tryPerform(TestPrincipal, params)
        extraProof.checkedAgain shouldBe listOf(extraMoved)
    }

    test("refuses a proven model the block writes without reading it through its proof") {
        val extra = employee(department = home.id())
        val params = first.run {
            Params(
                moving(proof(employee)),
                "two",
                extra = proven(proof(extra)) { extra },
                how = UNREAD,
                unread = extra,
            )
        }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe ungated("MoveEmployeeUow", extra.id().stringValue(), "Employee")
    }

    test("refuses an unread proven model where the unit of work lists another type in unguardedWrites") {
        val extra = employee(department = home.id())
        val params = first.run {
            Params(
                moving(proof(employee)),
                "two",
                extra = proven(proof(extra)) { extra },
                how = UNREAD,
                unread = extra,
            )
        }
        shouldThrow<IllegalStateException> { uow(setOf(Department::class)).tryPerform(TestPrincipal, params) }
            .message shouldBe ungated("MoveEmployeeUow", extra.id().stringValue(), "Employee")
    }

    test("refuses another model written in a proven model's place, though the block read the proof") {
        val extra = employee(department = home.id())
        val decoy = employee(department = home.id())
        val params = first.run {
            Params(
                moving(proof(employee)), "decoys", extra = proven(proof(extra)) { extra }, how = DECOY, decoy = decoy,
            )
        }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe ungated("MoveEmployeeUow", decoy.id().stringValue(), "Employee")
    }

    test("refuses a proven model an earlier attempt read through its proof, on an attempt that did not read it") {
        val extra = employee(department = home.id())
        val extraProof = proof(extra)
        val employeeProof = proof(employee)
        val params = first.run { Params(moving(employeeProof), "two", extra = proven(extraProof) { extra }) }
        uow().tryPerform(TestPrincipal, params)
        val again = retry.run {
            Params(
                moving(employeeProof), "two", extra = proven(extraProof) { extra }, how = UNREAD, unread = extra,
            )
        }
        shouldThrow<IllegalStateException> { uow(context = retryContext).tryPerform(TestPrincipal, again) }
            .message shouldBe ungated("MoveEmployeeUow", extra.id().stringValue(), "Employee")
    }

    test("updates a model of a type in unguardedWrites without a proof, and refuses to list a guarded type there") {
        val renamed = department("Old")
        shouldThrow<IllegalStateException> {
            uow().tryPerform(TestPrincipal, first.run { Params(moving(proof(employee)), "renames", renames = renamed) })
        }
        val params = first.run { Params(moving(proof(employee)), "renames", renames = renamed) }
        val changes = uow(setOf(Department::class)).tryPerform(TestPrincipal, params)
        changes.modelChangesToPersist.map { it.id } shouldContainExactly listOf(renamed.id(), employee.id())
        shouldThrow<IllegalArgumentException> { uow(setOf(Employee::class)) }
            .message shouldBe "MoveEmployeeUow lists [Employee] in unguardedWrites, and a model of each can be " +
            "guarded. Remove them, and write such models through Proven fields"
        shouldThrow<IllegalArgumentException> { uow(setOf(Model::class)) }
            .message shouldBe "MoveEmployeeUow lists [Model] in unguardedWrites, and a model of each can be " +
            "guarded. Remove them, and write such models through Proven fields"
    }

    test("a unit of work with no Proven field adds models, updates models of listed types, and no guarded model") {
        val renamed = department("Old")
        val hired = mutating { newEmployee(Name("Eve", "Green"), home.id(), "eve@test.com", BUBALEH) }
        val unproven = UnprovenUow(executionContext) { add(hired); update(renamed.rename("Renamed")) }
        unproven.tryPerform(TestPrincipal, ChildParams).modelChangesToPersist.map { it.id } shouldContainExactly
            listOf(hired.id(), renamed.id())
        val writing = UnprovenUow(executionContext) { update(employee) { changeDepartment(away) } }
        shouldThrow<IllegalStateException> { writing.tryPerform(TestPrincipal, ChildParams) }
            .message shouldBe ungated("UnprovenUow", employee.id().stringValue(), "Employee")
    }

    test("writes each of several models of one kind under its own proof, and refuses one left unwritten") {
        val team = listOf(employee, employee(department = home.id()), employee(department = home.id()))
        val bound = first.run { TeamParams(team.map { member -> proven(proof(member)) { member } }) }
        MoveTeamUow(executionContext, away, skipping = null).tryPerform(TestPrincipal, bound)
            .modelChangesToPersist.map { it.id } shouldContainExactly team.map { it.id() }
        val again = first.run { TeamParams(team.map { member -> proven(proof(member)) { member } }) }
        shouldThrow<IllegalStateException> {
            MoveTeamUow(executionContext, away, skipping = team[1].id()).tryPerform(TestPrincipal, again)
        }.message shouldBe unwritten("MoveTeamUow", listOf(team[1].id().stringValue()))
    }

    test("composes a child that writes an unguarded model, and refuses one that writes a guarded model") {
        val other = employee(department = home.id())
        val changes = composing { RenameDepartmentUow(it, home) }
            .tryPerform(TestPrincipal, first.params(proof(employee)))
        changes.modelChangesToPersist.map { it.id } shouldContainExactly listOf(home.id(), employee.id())
        shouldThrow<IllegalStateException> {
            composing { MoveOtherEmployeeUow(it, other, away) }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("ComposingGuardedUow", "MoveOtherEmployeeUow", other.id().stringValue())
    }

    test("refuses a child that writes the proven model itself, which only the block may write") {
        shouldThrow<IllegalStateException> {
            composing { MoveOtherEmployeeUow(it, employee, away) }
                .tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("ComposingGuardedUow", "MoveOtherEmployeeUow", employee.id().stringValue())
    }

    test("refuses a child that calls notChanged on a guarded model beside the proven one") {
        val other = employee(department = home.id())
        shouldThrow<IllegalStateException> {
            composing { HoldEmployeeUow(it, other) }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("ComposingGuardedUow", "HoldEmployeeUow", other.id().stringValue())
    }

    test("merges a composed child's writes after the proven model the block updated first") {
        val changes = targetFirst { ctx, _ -> RenameDepartmentUow(ctx, home) }
            .tryPerform(TestPrincipal, first.params(proof(employee)))
        changes.modelChangesToPersist.map { it.id } shouldContainExactly listOf(employee.id(), home.id())
    }

    test("refuses a child that updates again the proven model the block already updated") {
        shouldThrow<IllegalStateException> {
            targetFirst { ctx, movedTarget -> MoveOtherEmployeeUow(ctx, movedTarget, elsewhere) }
                .tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("TargetFirstGuardedUow", "MoveOtherEmployeeUow", employee.id().stringValue())
    }

    test("refuses a child built without its factory's context, whose changes would drop the proven model") {
        shouldThrow<IllegalStateException> {
            targetFirst { _, _ -> RenameDepartmentUow(executionContext, home) }
                .tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message.shouldNotBeNull() shouldStartWith
            "Composed RenameDepartmentUow did not start from the changes its parent holds now"
    }

    test("a block that catches a child's refusal writes its own changes and nothing of the child's") {
        val other = employee(department = home.id())
        val moving = { ctx: ExecutionContext -> MoveOtherEmployeeUow(ctx, other, away) }
        val changes = ComposingGuardedUow(executionContext, away, moving, swallowing = true)
            .tryPerform(TestPrincipal, first.params(proof(employee)))
        changes.modelChangesToPersist.map { it.id } shouldBe listOf(employee.id())
        changes.modelChangesToPersist.none { it is NoopModel } shouldBe true
    }

    test("a block that catches a child's refusal and writes nothing on the model it read is refused") {
        val swallowing = block { params ->
            val read = params.employee.model()
            runCatching { execute({ MoveOtherEmployeeUow(it, read, away) }, TestPrincipal) { ChildParams } }
            read
        }
        shouldThrow<IllegalStateException> { swallowing.tryPerform(TestPrincipal, first.params(proof(employee))) }
            .message shouldBe unwritten("Block", listOf(employee.id().stringValue()))
    }

    test("refuses a child that adds a guarded model") {
        val hired = mutating { newEmployee(Name("Eve", "Green"), home.id(), "eve@test.com", BUBALEH) }
        shouldThrow<IllegalStateException> {
            composing(plainChild("Hire") { add(hired); "hired" })
                .tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("ComposingGuardedUow", "Hire", hired.id().stringValue())
    }

    test("refuses a plain child that relays a grandchild's write of a guarded model, naming the child") {
        val other = employee(department = home.id())
        val grandchild = { ctx: ExecutionContext -> MoveOtherEmployeeUow(ctx, other, away) }
        shouldThrow<IllegalStateException> {
            composing(plainChild("Relay") { execute(grandchild, TestPrincipal) { ChildParams }; "relayed" })
                .tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("ComposingGuardedUow", "Relay", other.id().stringValue())
    }

    test("after a caught refusal a later child merges onto the block's models and entity changes as they were") {
        val other = employee(department = home.id())
        val tag = Tag.environmentTag(home.id().id, "production")
        var refusal: Throwable? = null
        val changes = block { params ->
            execute(plainChild("Tag") { add(tag); "tagged" }, TestPrincipal) { ChildParams }
            refusal = runCatching {
                execute({ MoveOtherEmployeeUow(it, other, away) }, TestPrincipal) { ChildParams }
            }.exceptionOrNull()
            execute({ RenameDepartmentUow(it, home) }, TestPrincipal) { ChildParams }
            update(params.employee.model()) { changeDepartment(away) }
        }.tryPerform(TestPrincipal, first.params(proof(employee)))
        refusal?.message shouldBe refusedWrite("Block", "MoveOtherEmployeeUow", other.id().stringValue())
        changes.modelChangesToPersist.map { it.id } shouldBe listOf(home.id(), employee.id())
        changes.entityChangesToPersist shouldBe listOf(AddEntity(tag))
    }

    test("does not count the owned models of an aggregate the block added as a child's writes") {
        val dept = newDeptAggregate(name = "Dept", boss = randomEmployeeId(), ration = BUBALEH).let { d ->
            d.addEmployee(mutating { newEmployee(Name("Eve", "Green"), d.id(), "eve@test.com", BUBALEH) })
        }
        val changes = block { params ->
            add(dept)
            execute({ RenameDepartmentUow(it, home) }, TestPrincipal) { ChildParams }
            update(params.employee.model()) { changeDepartment(away) }
        }.tryPerform(TestPrincipal, first.params(proof(employee)))
        changes.modelChangesToPersist.map { it.id } shouldBe
            listOf(dept.id(), dept.employees.single().id(), home.id(), employee.id())
    }

    test("refuses a child that adds an unguarded aggregate owning a new guarded model, naming the owned model") {
        val hired = mutating { newEmployee(Name("Eve", "Green"), home.id(), "eve@test.com", BUBALEH) }
        val dept = newDeptAggregate(name = "Dept", boss = randomEmployeeId(), ration = BUBALEH).addEmployee(hired)
        shouldThrow<IllegalStateException> {
            block { params ->
                execute(plainChild("Found") { add(dept); "founded" }, TestPrincipal) { ChildParams }
                update(params.employee.model()) { changeDepartment(away) }
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("Block", "Found", hired.id().stringValue())
    }

    test("onFailure gets the model the failed attempt read, or rethrows, and refuses one the attempt did not read") {
        val proof = proof(employee)
        first.moving(proof)
        val params = retry.run { Params(moving(proof, moved), "moves") }
        val stale = StaleRecordException(setOf(employee.id()), "employee")
        val answering = MoveEmployeeUow(retryContext, setOf(), away, answering = true)
        shouldThrow<IllegalStateException> { answering.onFailure(params, stale) }
            .message shouldBe "MoveEmployeeUow: onFailure asked for ${employee.id().stringValue()}, and the failed " +
            "attempt did not read that model with model(). Ask only for a Proven the block reads on every path"
        answering.tryPerform(TestPrincipal, params)
        answering.onFailure(params, stale) shouldBeSameInstanceAs moved
        shouldThrow<StaleRecordException> { uow().onFailure(params, stale) } shouldBeSameInstanceAs stale
    }

    test("refuses an added aggregate that owns a changed guarded model the block did not read with model()") {
        val other = employee(department = home.id())
        val dept = newDeptAggregate(name = "Fresh", boss = randomEmployeeId(), ration = BUBALEH)
            .addEmployee(mutating { other.changeDepartment(away) })
        shouldThrow<IllegalStateException> {
            block { params ->
                add(dept)
                update(params.employee.model()) { changeDepartment(away) }
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe ownedUnread(dept.id().stringValue(), other.id().stringValue())
    }

    test("refuses a stored aggregate kept with notChanged that owns a changed guarded model the block did not read") {
        val other = employee(department = home.id())
        val dept = storedDept(listOf(mutating { other.changeDepartment(away) }))
        shouldThrow<IllegalStateException> {
            block { params ->
                notChanged(dept)
                update(params.employee.model()) { changeDepartment(away) }
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe ownedUnread(dept.id().stringValue(), other.id().stringValue())
    }

    test("refuses an update of a listed aggregate that owns a changed guarded model the block did not read") {
        val other = employee(department = home.id())
        val dept = storedDept(listOf(mutating { other.changeDepartment(away) }))
        shouldThrow<IllegalStateException> {
            block(unguarded = setOf(DeptAggregate::class)) { params ->
                update(dept.rename("Renamed"))
                update(params.employee.model()) { changeDepartment(away) }
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe ownedUnread(dept.id().stringValue(), other.id().stringValue())
    }

    test("update(model) { } refuses a guarded model the block did not read, before it runs the mutation") {
        val other = employee(department = home.id())
        var mutated = false
        shouldThrow<IllegalStateException> {
            block { params ->
                update(other) {
                    mutated = true
                    changeDepartment(away)
                }
                update(params.employee.model()) { changeDepartment(away) }
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe ungated("Block", other.id().stringValue(), "Employee")
        mutated shouldBe false
    }

    test("update(model) { } refuses a mutation whose aggregate owns a changed guarded model the block did not read") {
        val other = employee(department = home.id())
        val dept = storedDept(listOf(mutating { other.changeDepartment(away) }))
        shouldThrow<IllegalStateException> {
            block(unguarded = setOf(DeptAggregate::class)) { params ->
                update(dept) { rename("Renamed") }
                update(params.employee.model()) { changeDepartment(away) }
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe ownedUnread(dept.id().stringValue(), other.id().stringValue())
    }

    test("update(model) { } refuses a mutation that returns the proven model at another version") {
        val stale = Employee(
            employee.id(), employee.name, home.id(), employee.email, BUBALEH, persistentState(version(2), null),
        )
        shouldThrow<IllegalStateException> {
            block { params -> update(params.employee.model()) { stale } }
                .tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe stale("Block", employee.id().stringValue())
    }

    test("update(model) { } refuses a mutation that returns a guarded type the block holds no proof for") {
        val orphan = department("Orphan")
        shouldThrow<IllegalStateException> {
            block(unguarded = setOf(OrphanedDepartment::class), guarding = Guarding(OwnedDepartment::class)) { params ->
                update(orphan) { addBoss(employee) }
                update(params.employee.model()) { changeDepartment(away) }
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe ungated("Block", orphan.id().stringValue(), "OwnedDepartment")
    }

    test("update(model) { } refuses a mutation that returns a type that is not in unguardedWrites") {
        val orphan = department("Orphan")
        shouldThrow<IllegalStateException> {
            block(unguarded = setOf(OrphanedDepartment::class)) { params ->
                update(orphan) { addBoss(employee) }
                update(params.employee.model()) { changeDepartment(away) }
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe unlisted("Block", orphan.id().stringValue(), "OwnedDepartment")
    }

    test("checks every model an aggregate owns, past a new one that comes first") {
        val other = employee(department = home.id())
        val hired = mutating { newEmployee(Name("Eve", "Green"), home.id(), "eve@test.com", BUBALEH) }
        val dept = newDeptAggregate(name = "Fresh", boss = randomEmployeeId(), ration = BUBALEH)
            .addEmployee(hired)
            .addEmployee(mutating { other.changeDepartment(away) })
        shouldThrow<IllegalStateException> {
            block { params ->
                add(dept)
                update(params.employee.model()) { changeDepartment(away) }
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe ownedUnread(dept.id().stringValue(), other.id().stringValue())
    }

    test("add { } refuses before it adds, so a block that catches the refusal holds nothing of the aggregate") {
        val other = employee(department = home.id())
        val dept = newDeptAggregate(name = "Fresh", boss = randomEmployeeId(), ration = BUBALEH)
            .addEmployee(mutating { other.changeDepartment(away) })
        var refusal: Throwable? = null
        val changes = block { params ->
            refusal = runCatching { add { dept } }.exceptionOrNull()
            update(params.employee.model()) { changeDepartment(away) }
        }.tryPerform(TestPrincipal, first.params(proof(employee)))
        refusal?.message shouldBe ownedUnread(dept.id().stringValue(), other.id().stringValue())
        changes.modelChangesToPersist.map { it.id } shouldBe listOf(employee.id())
    }

    test("refuses a stubbed child that claims a guarded model, and keeps the block's changes") {
        val other = employee(department = home.id())
        var refusal: Throwable? = null
        val changes = block { params ->
            val moved = update(params.employee.model()) { changeDepartment(away) }
            refusal = runCatching { execute(stubbedChild("Stub", other), TestPrincipal) { ChildParams } }
                .exceptionOrNull()
            moved
        }.tryPerform(TestPrincipal, first.params(proof(employee)))
        refusal?.message shouldBe refusedWrite("Block", "Stub", other.id().stringValue())
        changes.modelChangesToPersist.map { it.id } shouldBe listOf(employee.id())
    }

    test("refuses a stubbed child that claims the stored copy of the proven model") {
        shouldThrow<IllegalStateException> {
            block { params ->
                val moved = update(params.employee.model()) { changeDepartment(away) }
                execute(stubbedChild("Stub", employee), TestPrincipal) { ChildParams }
                moved
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("Block", "Stub", employee.id().stringValue())
    }

    test("update(model) { } that hands back the proven model keeps it unchanged") {
        val changes = block { params -> update(params.employee.model()) { this } }
            .tryPerform(TestPrincipal, first.params(proof(employee)))
        changes.modelChangesToPersist.single().shouldBeInstanceOf<NoopModel>().id shouldBe employee.id()
    }

    test("add { } refuses a created aggregate that owns a changed guarded model the block did not read") {
        val other = employee(department = home.id())
        val dept = newDeptAggregate(name = "Fresh", boss = randomEmployeeId(), ration = BUBALEH)
            .addEmployee(mutating { other.changeDepartment(away) })
        shouldThrow<IllegalStateException> {
            block { params ->
                add { dept }
                update(params.employee.model()) { changeDepartment(away) }
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe ownedUnread(dept.id().stringValue(), other.id().stringValue())
    }

    test("writes a proven model through the aggregate that owns it, at the version model() returned") {
        val changes = block(unguarded = setOf(DeptAggregate::class)) { params ->
            val moved = params.employee.model().let { mutating { it.changeDepartment(away) } }
            update(storedDept(listOf(moved)).rename("Renamed"))
            moved
        }.tryPerform(TestPrincipal, first.params(proof(employee)))
        changes.modelChangesToPersist.map { it.id } shouldContainExactly listOf(home.id(), employee.id())
    }

    test("refuses a block whose child rebuilds its aggregate without the proven model the aggregate owned") {
        shouldThrow<IllegalStateException> {
            block(unguarded = setOf(DeptAggregate::class)) { params ->
                val moved = params.employee.model().let { mutating { it.changeDepartment(away) } }
                val dept = update(storedDept(listOf(moved)).rename("Renamed"))
                val rebuilt = DeptAggregate(
                    dept.id(), "Again", dept.boss, 1, BUBALEH, listOf<Employee>(),
                    dept.raise(NameChanged(dept.id(), "Renamed", "Again")),
                )
                execute(plainChild("Rebuild") { update(rebuilt); "rebuilt" }, TestPrincipal) { ChildParams }
                moved
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe unwritten("Block", listOf(employee.id().stringValue()))
    }

    test("refuses a child's write of a model whose type is a subtype of a guarded type") {
        shouldThrow<IllegalStateException> {
            block(guarding = Guarding(Department::class)) { params ->
                execute({ RenameDepartmentUow(it, home) }, TestPrincipal) { ChildParams }
                update(params.employee.model()) { changeDepartment(away) }
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("Block", "RenameDepartmentUow", home.id().stringValue())
    }

    test("refuses a Proven in a block that runs in another run, or in another attempt of its run") {
        val params = first.run { Params(moving(proof(employee)), "moves") }
        shouldThrow<IllegalStateException> {
            uow(context = noAttempt.forAttempt(initial(TestPrincipal))).tryPerform(TestPrincipal, params)
        }.message shouldBe boundElsewhere(employee.id().stringValue())
        shouldThrow<IllegalStateException> {
            uow(context = retryContext).tryPerform(TestPrincipal, params)
        }.message shouldBe boundElsewhere(employee.id().stringValue())
        shouldThrow<IllegalStateException> {
            val sameRun = (first as InstantiationContext.External).run
            val again = noAttempt.forAttempt(InstantiationContext.External(0, TestPrincipal, sameRun))
            uow(context = again).tryPerform(TestPrincipal, params)
        }.message shouldBe boundElsewhere(employee.id().stringValue())
        uow().tryPerform(TestPrincipal, params).result.departmentId shouldBe away.id()
    }

    test("a unit of work built with a context that carries no attempt reads no Proven") {
        val params = first.run { Params(moving(proof(employee)), "moves") }
        shouldThrow<IllegalStateException> { uow(context = noAttempt).tryPerform(TestPrincipal, params) }
            .message shouldBe attemptless("MoveEmployeeUow", employee.id().stringValue())
        uow().tryPerform(TestPrincipal, params).result.departmentId shouldBe away.id()
    }

    test("a composed child gets a context that carries no attempt, and its parent's carries the parent's attempt") {
        var childContext: ExecutionContext? = null
        val renaming = { ctx: ExecutionContext ->
            childContext = ctx
            RenameDepartmentUow(ctx, home)
        }
        block { params ->
            execute(renaming, TestPrincipal) { ChildParams }
            update(params.employee.model()) { changeDepartment(away) }
        }.tryPerform(TestPrincipal, first.params(proof(employee)))
        checkNotNull(childContext).attempt shouldBe null
        executionContext.attempt shouldBeSameInstanceAs first
    }

    test("an anonymous guarded unit of work calls itself a guarded unit of work in its refusals") {
        val anonymous = object : GuardedUnitOfWork<TestPrincipal, TestProof<*>, EmployeeParams, Employee>(
            executionContext, Configuration.default(), EmployeesGuarded, setOf(),
        ) {
            override suspend fun tryPerform(principal: TestPrincipal, params: EmployeeParams) =
                changes { params.employee.model() }
        }
        shouldThrow<IllegalStateException> { anonymous.tryPerform(TestPrincipal, first.params(proof(employee))) }
            .message shouldBe unwritten("A guarded unit of work", listOf(employee.id().stringValue()))
    }
})
