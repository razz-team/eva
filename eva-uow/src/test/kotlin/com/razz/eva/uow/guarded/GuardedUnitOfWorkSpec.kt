package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
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
import com.razz.eva.domain.Version.Companion.version
import com.razz.eva.domain.addEmployee
import com.razz.eva.persistence.PersistenceException.StaleRecordException
import com.razz.eva.uow.AddEntity
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.InstantiationContextFixtures.afterRetry
import com.razz.eva.uow.InstantiationContextFixtures.initial
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UpdateModel
import com.razz.eva.uow.composable.ChangesDsl
import com.razz.eva.uow.composable.UnitOfWork
import com.razz.eva.uow.guarded.Fixtures.department
import com.razz.eva.uow.guarded.Fixtures.employee
import com.razz.eva.uow.guarded.Fixtures.proof
import com.razz.eva.uow.guarded.MoveEmployeeUow.How.DECOY
import com.razz.eva.uow.guarded.MoveEmployeeUow.How.PROVEN
import com.razz.eva.uow.guarded.MoveEmployeeUow.How.UNDECIDED
import com.razz.eva.uow.guarded.MoveEmployeeUow.Params
import com.razz.eva.uow.guarded.Proven.Companion.proven
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.opentelemetry.api.OpenTelemetry
import kotlin.reflect.KClass

class GuardedUnitOfWorkSpec : FunSpec({

    val executionContext = ExecutionContext(fixedUTC(millisUTC().instant()), OpenTelemetry.noop())
    val home = department("Home")
    val away = department("Away")
    val elsewhere = department("Elsewhere")
    val employee = employee(department = home.id())
    val moved = employee(id = employee.id(), department = elsewhere.id())
    val first = initial(TestPrincipal)
    val retry = afterRetry(first)
    fun uow(unguarded: Set<KClass<out Model<*, *>>> = setOf()) = MoveEmployeeUow(executionContext, unguarded, away)
    fun InstantiationContext.moving(proof: TestProof<Employee>, reloaded: Employee = moved) =
        proven(proof) { reloaded }
    fun InstantiationContext.params(proof: TestProof<Employee>) = EmployeeParams(moving(proof))
    fun <R : Any> composing(child: (ExecutionContext) -> UnitOfWork<TestPrincipal, ChildParams, R>) =
        ComposingGuardedUow(executionContext, away, child, swallowing = false)
    fun <R : Any> targetFirst(child: (ExecutionContext, Employee) -> UnitOfWork<TestPrincipal, ChildParams, R>) =
        TargetFirstGuardedUow(executionContext, away, child)
    fun refusedWrite(uow: String, id: String) = "Composed unit of work [$uow] wrote [$id], which its parent refuses"
    fun ungated(id: Any) = "A guarded unit of work updates only a model a proof decided in its block " +
        "or a declared type, and $id is neither"
    fun unwritten(uow: String, ids: List<Any>) =
        "$uow decided ${ids.map { it.toString() }} and wrote nothing on them: update each, or hold it with notChanged"
    fun plainChild(name: String, body: suspend ChangesDsl.() -> String) = { ctx: ExecutionContext ->
        object : UnitOfWork<TestPrincipal, ChildParams, String>(ctx) {
            override fun name() = name
            override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) = changes(body)
        }
    }
    fun block(body: suspend GuardedChangesDsl<TestProof<*>>.(EmployeeParams) -> Employee) =
        object : GuardedUnitOfWork<TestPrincipal, TestProof<*>, EmployeeParams, Employee>(
            executionContext, Configuration.default(), EmployeesGuarded, setOf(),
        ) {
            override fun name() = "Block"
            override suspend fun tryPerform(principal: TestPrincipal, params: EmployeeParams) =
                changes { body(params) }
        }

    test("writes a proven model under its proof and returns changes of the one sealed type") {
        val changes = uow().tryPerform(TestPrincipal, first.run { Params(moving(proof(employee)), "moves") })
        changes.shouldBeInstanceOf<GuardedChanges<Employee>>()
        val change = changes.modelChangesToPersist.single().shouldBeInstanceOf<UpdateModel<*, *, *>>()
        change.model.shouldBeInstanceOf<Employee>().departmentId shouldBe away.id()
        change.modelEvents shouldBe listOf(DepartmentChanged(employee.id(), home.id(), away.id()))
        changes.result shouldBeSameInstanceAs change.model
        GuardedChanges::class.sealedSubclasses.map { it.qualifiedName } shouldBe
            listOf("com.razz.eva.uow.guarded.GuardedUnitOfWork.Produced")
    }

    test("keeps the decided model on the first attempt and never reloads it") {
        val never: Employee by lazy { error("the first attempt does not reload") }
        val proof = proof(employee)
        val changes = uow().tryPerform(TestPrincipal, first.run { Params(proven(proof) { never }, "moves") })
        changes.result.id() shouldBe employee.id()
        proof.decidedAgain shouldBe listOf()
    }

    test("reloads a proven model on a retry and lets the proof decide again on what it loaded") {
        val proof = proof(employee)
        first.moving(proof)
        val changes = uow().tryPerform(TestPrincipal, retry.run { Params(moving(proof, moved), "moves") })
        proof.decidedAgain shouldBe listOf(moved)
        val change = changes.modelChangesToPersist.single().shouldBeInstanceOf<UpdateModel<*, *, *>>()
        change.modelEvents shouldBe listOf(DepartmentChanged(employee.id(), elsewhere.id(), away.id()))
    }

    test("a retry whose reload the proof refuses writes nothing") {
        val proof = proof(employee, refused = setOf(elsewhere))
        first.moving(proof)
        shouldThrow<Refused> { uow().tryPerform(TestPrincipal, retry.run { Params(moving(proof), "moves") }) }
            .message shouldBe "moved out of reach"
    }

    test("a reload that answers another model is refused before the proof is asked") {
        val proof = proof(employee)
        val other = employee()
        first.moving(proof)
        shouldThrow<IllegalStateException> {
            uow().tryPerform(TestPrincipal, retry.run { Params(moving(proof, other), "moves") })
        }.message shouldBe "The reload answered ${other.id().stringValue()} " +
            "for the proof's ${employee.id().stringValue()}"
        proof.decidedAgain shouldBe listOf()
    }

    test("binds a proof to one unit of work: a second first attempt on it is refused, a retry on it is not") {
        val proof = proof(employee)
        first.moving(proof)
        retry.moving(proof)
        shouldThrow<IllegalStateException> { first.moving(proof) }
            .message shouldBe "A proof for test.write is spent once, and this one was"
    }

    test("binds a retry only on a proof the first attempt of its own run spent") {
        val unspent = proof(employee)
        shouldThrow<IllegalStateException> { retry.moving(unspent) }
            .message shouldBe "A proof for test.write binds a retry only of the run that spent it"
        val spent = proof(employee)
        initial(TestPrincipal).moving(spent)
        shouldThrow<IllegalStateException> { retry.moving(spent) }
            .message shouldBe "A proof for test.write binds a retry only of the run that spent it"
        first.moving(unspent)
    }

    test("refuses to bind a proof minted for another principal, and leaves it unspent") {
        val proof = TestProof(OtherPrincipal, employee) { }
        shouldThrow<IllegalStateException> { first.moving(proof) }
            .message shouldBe "A proof for test.write on ${employee.id().stringValue()} " +
            "was minted for another principal"
        initial(OtherPrincipal).moving(proof)
    }

    test("refuses to bind a proof in a composed child's params, and leaves it unspent") {
        val proof = proof(employee)
        val parent = block { params ->
            execute({ RenameDepartmentUow(it, home) }, TestPrincipal) {
                moving(proof)
                ChildParams
            }
            update(params.employee.model().changeDepartment(away))
        }
        shouldThrow<IllegalStateException> { parent.tryPerform(TestPrincipal, first.params(proof(employee))) }
            .message shouldBe "A proof binds in the params a caller builds for a unit of work, " +
            "not in a composed child's"
        first.moving(proof)
    }

    test("refuses to bind a proof on a model that is not stored") {
        val hired = newEmployee(Name("Eve", "Green"), home.id(), "eve@test.com", BUBALEH)
        shouldThrow<IllegalArgumentException> { first.run { proven(TestProof(TestPrincipal, hired) { }) { hired } } }
            .message shouldBe "A proof binds a stored model, and ${hired.id().stringValue()} is not stored"
    }

    test("reads a proven model only in the block of the attempt that bound it, not in a second block on its params") {
        val params = first.run { Params(moving(proof(employee)), "moves") }
        uow().tryPerform(TestPrincipal, params)
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe "A proven model is read in the block of the attempt that bound it, " +
            "and ${employee.id().stringValue()} was read before"
    }

    test("refuses a block that reads a proven model and writes nothing on it") {
        val params = first.run { Params(moving(proof(employee)), "reads", skips = true) }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe unwritten("MoveEmployeeUow", listOf(employee.id().stringValue()))
    }

    test("holds a proven model with notChanged, a version check on the decided model") {
        val params = first.run { Params(moving(proof(employee)), "holds", holds = true) }
        uow().tryPerform(TestPrincipal, params).modelChangesToPersist.single() shouldBe NoopModel(employee)
    }

    test("refuses a block that adds a new model and only reads its proven model") {
        val added = Department.newDepartment("New", employee.id(), 1, BUBALEH)
        val params = first.run { Params(moving(proof(employee)), "adds", adds = added, skips = true) }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe unwritten("MoveEmployeeUow", listOf(employee.id().stringValue()))
    }

    test("refuses a decided model written at a version other than the one decided") {
        val stale = Employee(
            employee.id(), employee.name, home.id(), employee.email, BUBALEH, persistentState(version(2), null),
        )
        val params = first.run { Params(moving(proof(employee)), "stale", stale = stale) }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe "${employee.id().stringValue()} was decided at version 1, and the block writes it at 2"
    }

    test("refuses to hold a decided model at a version other than the one decided") {
        val stale = Employee(
            employee.id(), employee.name, home.id(), employee.email, BUBALEH, persistentState(version(2), null),
        )
        val params = first.run { Params(moving(proof(employee)), "holds stale", holds = true, stale = stale) }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe "${employee.id().stringValue()} was decided at version 1, and the block writes it at 2"
    }

    test("refuses two proofs that decide one model in one block") {
        val params = first.run {
            Params(moving(proof(employee)), "twice", extra = moving(proof(employee)), how = PROVEN)
        }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe "A block decides ${employee.id().stringValue()} once, and two proofs decided it"
    }

    test("refuses an update beside the proven model, though the block updates the proven model too") {
        val beside = employee(department = home.id())
        val params = first.run { Params(moving(proof(employee)), "moves two", beside = beside) }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe ungated(beside.id().stringValue())
    }

    test("writes two models, each read through its own proof") {
        val extra = employee(department = home.id())
        val params = first.run {
            Params(moving(proof(employee)), "moves two", extra = proven(proof(extra)) { error("first") })
        }
        val changes = uow().tryPerform(TestPrincipal, params)
        changes.modelChangesToPersist.map { it.id } shouldContainExactly listOf(extra.id(), employee.id())
    }

    test("decides a second proven model again on a retry") {
        val extra = employee(department = home.id())
        val extraMoved = employee(id = extra.id(), department = elsewhere.id())
        val extraProof = proof(extra)
        val employeeProof = proof(employee)
        first.run { Params(moving(employeeProof), "first", extra = proven(extraProof) { error("first") }) }
        val params = retry.run {
            Params(moving(employeeProof), "moves two", extra = proven(extraProof) { extraMoved })
        }
        uow().tryPerform(TestPrincipal, params)
        extraProof.decidedAgain shouldBe listOf(extraMoved)
    }

    test("refuses a proven model the block writes without reading it through its proof") {
        val extra = employee(department = home.id())
        val params = first.run {
            Params(
                moving(proof(employee)),
                "two",
                extra = proven(proof(extra)) { extra },
                how = UNDECIDED,
                undecided = extra,
            )
        }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, params) }
            .message shouldBe ungated(extra.id().stringValue())
    }

    test("refuses an unread proven model where the unit of work declares another aggregate") {
        val extra = employee(department = home.id())
        val params = first.run {
            Params(
                moving(proof(employee)),
                "two",
                extra = proven(proof(extra)) { extra },
                how = UNDECIDED,
                undecided = extra,
            )
        }
        shouldThrow<IllegalStateException> { uow(setOf(Department::class)).tryPerform(TestPrincipal, params) }
            .message shouldBe ungated(extra.id().stringValue())
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
            .message shouldBe ungated(decoy.id().stringValue())
    }

    test("refuses a proven model an earlier attempt read through its proof, on an attempt that did not read it") {
        val extra = employee(department = home.id())
        val extraProof = proof(extra)
        val employeeProof = proof(employee)
        val params = first.run { Params(moving(employeeProof), "two", extra = proven(extraProof) { extra }) }
        uow().tryPerform(TestPrincipal, params)
        val again = retry.run {
            Params(
                moving(employeeProof), "two", extra = proven(extraProof) { extra }, how = UNDECIDED, undecided = extra,
            )
        }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, again) }
            .message shouldBe ungated(extra.id().stringValue())
    }

    test("updates a model of a declared aggregate without a proof, and refuses to declare a guarded one") {
        val renamed = department("Old")
        shouldThrow<IllegalStateException> {
            uow().tryPerform(TestPrincipal, first.run { Params(moving(proof(employee)), "renames", renames = renamed) })
        }
        val params = first.run { Params(moving(proof(employee)), "renames", renames = renamed) }
        val changes = uow(setOf(Department::class)).tryPerform(TestPrincipal, params)
        changes.modelChangesToPersist.map { it.id } shouldContainExactly listOf(renamed.id(), employee.id())
        shouldThrow<IllegalArgumentException> { uow(setOf(Employee::class)) }
            .message shouldBe "MoveEmployeeUow writes Employee without a proof, and that reaches a guarded model"
        shouldThrow<IllegalArgumentException> { uow(setOf(Model::class)) }
            .message shouldBe "MoveEmployeeUow writes Model without a proof, and that reaches a guarded model"
    }

    test("a unit of work with no proven field adds models and writes declared ones, and no guarded one") {
        val renamed = department("Old")
        val hired = newEmployee(Name("Eve", "Green"), home.id(), "eve@test.com", BUBALEH)
        val unproven = UnprovenUow(executionContext) { add(hired); update(renamed.rename("Renamed")) }
        unproven.tryPerform(TestPrincipal, ChildParams).modelChangesToPersist.map { it.id } shouldContainExactly
            listOf(hired.id(), renamed.id())
        val writing = UnprovenUow(executionContext) { update(employee.changeDepartment(away)) }
        shouldThrow<IllegalStateException> { writing.tryPerform(TestPrincipal, ChildParams) }
            .message shouldBe ungated(employee.id().stringValue())
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
        }.message shouldBe refusedWrite("MoveOtherEmployeeUow", other.id().stringValue())
    }

    test("refuses a child that writes the proven model itself, which only the block's update covers") {
        shouldThrow<IllegalStateException> {
            composing { MoveOtherEmployeeUow(it, employee, away) }
                .tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("MoveOtherEmployeeUow", employee.id().stringValue())
    }

    test("refuses a child that holds a guarded model beside the proven one as unchanged, a version check unproven") {
        val other = employee(department = home.id())
        shouldThrow<IllegalStateException> {
            composing { HoldEmployeeUow(it, other) }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("HoldEmployeeUow", other.id().stringValue())
    }

    test("lands a composed child's writes after the proven model the block updated first") {
        val changes = targetFirst { ctx, _ -> RenameDepartmentUow(ctx, home) }
            .tryPerform(TestPrincipal, first.params(proof(employee)))
        changes.modelChangesToPersist.map { it.id } shouldContainExactly listOf(employee.id(), home.id())
    }

    test("refuses a child that merges onto the decided instance the block updated first") {
        shouldThrow<IllegalStateException> {
            targetFirst { ctx, movedTarget -> MoveOtherEmployeeUow(ctx, movedTarget, elsewhere) }
                .tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("MoveOtherEmployeeUow", employee.id().stringValue())
    }

    test("refuses a child whose hand-built changes drop the proven model the block updated first") {
        shouldThrow<IllegalStateException> {
            targetFirst { ctx, _ -> HandBuiltChildUow(ctx, home) }
                .tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe "Composed unit of work [HandBuiltChildUow] dropped models " +
            "[${employee.id().stringValue()}] its parent had registered. Build its changes with changes { } over " +
            "the ExecutionContext its factory is given."
    }

    test("a swallowed refusal lands the block's own write and nothing of the child's") {
        val other = employee(department = home.id())
        val moving = { ctx: ExecutionContext -> MoveOtherEmployeeUow(ctx, other, away) }
        val changes = ComposingGuardedUow(executionContext, away, moving, swallowing = true)
            .tryPerform(TestPrincipal, first.params(proof(employee)))
        changes.modelChangesToPersist.map { it.id } shouldBe listOf(employee.id())
        changes.modelChangesToPersist.none { it is NoopModel } shouldBe true
    }

    test("a swallowed refusal of a child's write on a decided model leaves that model to the rule") {
        val swallowing = block { params ->
            val decided = params.employee.model()
            runCatching { execute({ MoveOtherEmployeeUow(it, decided, away) }, TestPrincipal) { ChildParams } }
            decided
        }
        shouldThrow<IllegalStateException> { swallowing.tryPerform(TestPrincipal, first.params(proof(employee))) }
            .message shouldBe unwritten("Block", listOf(employee.id().stringValue()))
    }

    test("refuses a child that adds a guarded model") {
        val hired = newEmployee(Name("Eve", "Green"), home.id(), "eve@test.com", BUBALEH)
        shouldThrow<IllegalStateException> {
            composing(plainChild("Hire") { add(hired); "hired" })
                .tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("Hire", hired.id().stringValue())
    }

    test("refuses a plain child that relays a grandchild's write of a guarded model, naming the child") {
        val other = employee(department = home.id())
        val grandchild = { ctx: ExecutionContext -> MoveOtherEmployeeUow(ctx, other, away) }
        shouldThrow<IllegalStateException> {
            composing(plainChild("Relay") { execute(grandchild, TestPrincipal) { ChildParams }; "relayed" })
                .tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("Relay", other.id().stringValue())
    }

    test("after a swallowed refusal a later child merges onto the block's models and entity changes as they were") {
        val other = employee(department = home.id())
        val tag = Tag.environmentTag(home.id().id, "production")
        var refusal: Throwable? = null
        val changes = block { params ->
            execute(plainChild("Tag") { add(tag); "tagged" }, TestPrincipal) { ChildParams }
            refusal = runCatching {
                execute({ MoveOtherEmployeeUow(it, other, away) }, TestPrincipal) { ChildParams }
            }.exceptionOrNull()
            execute({ RenameDepartmentUow(it, home) }, TestPrincipal) { ChildParams }
            update(params.employee.model().changeDepartment(away))
        }.tryPerform(TestPrincipal, first.params(proof(employee)))
        refusal?.message shouldBe refusedWrite("MoveOtherEmployeeUow", other.id().stringValue())
        changes.modelChangesToPersist.map { it.id } shouldBe listOf(home.id(), employee.id())
        changes.entityChangesToPersist shouldBe listOf(AddEntity(tag))
    }

    test("does not count the owned models of an aggregate the block added as a child's writes") {
        val dept = newDeptAggregate(name = "Dept", boss = randomEmployeeId(), ration = BUBALEH).let { d ->
            d.addEmployee(newEmployee(Name("Eve", "Green"), d.id(), "eve@test.com", BUBALEH))
        }
        val changes = block { params ->
            add(dept)
            execute({ RenameDepartmentUow(it, home) }, TestPrincipal) { ChildParams }
            update(params.employee.model().changeDepartment(away))
        }.tryPerform(TestPrincipal, first.params(proof(employee)))
        changes.modelChangesToPersist.map { it.id } shouldBe
            listOf(dept.id(), dept.employees.single().id(), home.id(), employee.id())
    }

    test("refuses a child that adds an unguarded aggregate owning a new guarded model, naming the owned model") {
        val hired = newEmployee(Name("Eve", "Green"), home.id(), "eve@test.com", BUBALEH)
        val dept = newDeptAggregate(name = "Dept", boss = randomEmployeeId(), ration = BUBALEH).addEmployee(hired)
        shouldThrow<IllegalStateException> {
            block { params ->
                execute(plainChild("Found") { add(dept); "founded" }, TestPrincipal) { ChildParams }
                update(params.employee.model().changeDepartment(away))
            }.tryPerform(TestPrincipal, first.params(proof(employee)))
        }.message shouldBe refusedWrite("Found", hired.id().stringValue())
    }

    test("answers a failed write with the model the attempt decided, or rethrows, and none the attempt did not read") {
        val proof = proof(employee)
        first.moving(proof)
        val params = retry.run { Params(moving(proof, moved), "moves") }
        val stale = StaleRecordException(setOf(employee.id()), "employee")
        val answering = MoveEmployeeUow(executionContext, setOf(), away, answering = true)
        shouldThrow<IllegalStateException> { answering.onFailure(params, stale) }
            .message shouldBe "MoveEmployeeUow: a failed write is answered only on a model its attempt decided, " +
            "and ${employee.id().stringValue()} was not"
        answering.tryPerform(TestPrincipal, params)
        answering.onFailure(params, stale) shouldBeSameInstanceAs moved
        shouldThrow<StaleRecordException> { uow().onFailure(params, stale) } shouldBeSameInstanceAs stale
    }
})
