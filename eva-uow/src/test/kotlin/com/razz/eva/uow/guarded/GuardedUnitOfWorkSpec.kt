package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.DeptAggregate.Companion.newDeptAggregate
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeEvent.DepartmentChanged
import com.razz.eva.domain.EmployeeId.Companion.randomEmployeeId
import com.razz.eva.domain.Model
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Tag
import com.razz.eva.domain.addEmployee
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.persistence.PersistenceException.StaleRecordException
import com.razz.eva.uow.AddEntity
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.InstantiationContextFixtures.AFTER_RETRY
import com.razz.eva.uow.InstantiationContextFixtures.INITIAL
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UpdateModel
import com.razz.eva.uow.composable.ChangesDsl
import com.razz.eva.uow.composable.UnitOfWork
import com.razz.eva.uow.guarded.Fixtures.department
import com.razz.eva.uow.guarded.Fixtures.employee
import com.razz.eva.uow.guarded.Fixtures.proof
import com.razz.eva.uow.guarded.Guarded.Companion.guarded
import com.razz.eva.uow.guarded.MoveEmployeeUow.How.DECOY
import com.razz.eva.uow.guarded.MoveEmployeeUow.How.PROVEN
import com.razz.eva.uow.guarded.MoveEmployeeUow.How.UNDECIDED
import com.razz.eva.uow.guarded.MoveEmployeeUow.Params
import com.razz.eva.uow.guarded.Proven.Companion.proven
import com.razz.eva.uow.params.kotlinx.UowParams
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
    fun uow(unguarded: Set<KClass<out Model<*, *>>> = setOf()) = MoveEmployeeUow(executionContext, unguarded, away)
    fun <P : UowParams<P>> InstantiationContext.guardedOn(
        proof: TestProof<Employee>,
        params: P,
        reloaded: Employee = moved,
    ) = guarded(proof, params) { reloaded }
    fun <R : Any> composing(child: (ExecutionContext) -> UnitOfWork<TestPrincipal, ChildParams, R>) =
        ComposingGuardedUow(executionContext, away, child, swallowing = false)
    fun <R : Any> targetFirst(child: (ExecutionContext, Employee) -> UnitOfWork<TestPrincipal, ChildParams, R>) =
        TargetFirstGuardedUow(executionContext, away, child)
    fun refusedWrite(uow: String, id: String) = "Composed unit of work [$uow] wrote [$id], which its parent refuses"
    fun plainChild(name: String, body: suspend ChangesDsl.() -> String) = { ctx: ExecutionContext ->
        object : UnitOfWork<TestPrincipal, ChildParams, String>(ctx) {
            override fun name() = name
            override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) = changes(body)
        }
    }
    fun block(body: suspend GuardedChangesDsl<TestPrincipal, Employee>.() -> Employee) =
        object : GuardedUnitOfWork<TestPrincipal, TestProof<Employee>, ComposingGuardedUow.Params, Employee, Employee>(
            executionContext, Configuration.default(), EmployeesGuarded, setOf(),
        ) {
            override suspend fun perform(principal: TestPrincipal, params: ComposingGuardedUow.Params) = changes(body)
        }

    test("writes its target under the proof and returns changes of the one sealed type") {
        val changes = uow().tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), Params("moves")))
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
        val changes = uow().tryPerform(TestPrincipal, INITIAL.guarded(proof, Params("moves")) { never })
        changes.result.id() shouldBe employee.id()
        proof.decidedAgain shouldBe listOf()
    }

    test("reloads the target on a retry and lets the proof decide again on what it loaded") {
        val proof = proof(employee)
        val changes = uow().tryPerform(TestPrincipal, AFTER_RETRY.guardedOn(proof, Params("moves"), moved))
        proof.decidedAgain shouldBe listOf(moved)
        val change = changes.modelChangesToPersist.single().shouldBeInstanceOf<UpdateModel<*, *, *>>()
        change.modelEvents shouldBe listOf(DepartmentChanged(employee.id(), elsewhere.id(), away.id()))
    }

    test("a retry whose reload the proof refuses does not run the body") {
        val proof = proof(employee, refused = setOf(elsewhere))
        shouldThrow<Refused> { uow().tryPerform(TestPrincipal, AFTER_RETRY.guardedOn(proof, Params("moves"))) }
            .message shouldBe "moved out of reach"
    }

    test("a reload that answers another model is refused before the proof is asked") {
        val proof = proof(employee)
        val other = employee()
        shouldThrow<IllegalStateException> {
            uow().tryPerform(TestPrincipal, AFTER_RETRY.guardedOn(proof, Params("moves"), other))
        }.message shouldBe "The reload answered ${other.id().stringValue()} " +
            "for the proof's ${employee.id().stringValue()}"
        proof.decidedAgain shouldBe listOf()
    }

    test("binds a proof to one run: a second first attempt on it is refused, a retry on it is not") {
        val proof = proof(employee)
        INITIAL.guardedOn(proof, Params("first"))
        AFTER_RETRY.guardedOn(proof, Params("retried"))
        shouldThrow<IllegalStateException> { INITIAL.guardedOn(proof, Params("second")) }
            .message shouldBe "A proof for test.write runs once, and this one ran"
        proof.lent shouldBe false
    }

    test("refuses a proof minted for another principal") {
        val proof = TestProof(OtherPrincipal, employee) { }
        val guarded = INITIAL.guarded(proof, Params("moves")) { moved }
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, guarded) }
            .message shouldBe "MoveEmployeeUow was handed a proof minted for another principal"
    }

    test("refuses a block that only reads its target") {
        val guarded = INITIAL.guardedOn(proof(employee), Params("reads", readOnly = true))
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, guarded) }
            .message shouldBe "MoveEmployeeUow runs test.write without writing ${employee.id().stringValue()}"
    }

    test("refuses a block that adds a new model and only reads its target") {
        val added = Department.newDepartment("New", employee.id(), 1, BUBALEH)
        val guarded = INITIAL.guardedOn(proof(employee), Params("adds", adds = added, readOnly = true))
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, guarded) }
            .message shouldBe "MoveEmployeeUow runs test.write without writing ${employee.id().stringValue()}"
    }

    test("refuses an update beside the target, though the block updates the target too") {
        val beside = employee(department = home.id())
        val guarded = INITIAL.guardedOn(proof(employee), Params("moves two", beside = beside))
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, guarded) }
            .message shouldBe "A guarded unit of work updates only its target ${employee.id().stringValue()}, " +
            "not ${beside.id().stringValue()}, which is neither proven nor declared"
    }

    test("writes one more model under its own proof, read through it, and spends that proof") {
        val extra = employee(department = home.id())
        val extraProof = proof(extra)
        val params = INITIAL.run { Params("moves two", extra = proven(extraProof) { error("first attempt") }) }
        val changes = uow().tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), params))
        changes.modelChangesToPersist.map { it.id } shouldContainExactly listOf(extra.id(), employee.id())
        extraProof.lent shouldBe true
        shouldThrow<IllegalStateException> { extraProof.run() }
            .message shouldBe "A proof for test.write runs once, and this one ran"
        shouldThrow<IllegalStateException> { extraProof.lend() }
            .message shouldBe "A proof for test.write covers one write, and this one ran"
    }

    test("decides a proven model again on a retry, once an attempt lent its proof") {
        val extra = employee(department = home.id())
        val extraMoved = employee(id = extra.id(), department = elsewhere.id())
        val extraProof = proof(extra)
        INITIAL.run { proven(extraProof) { error("first attempt") } }.decided()
        val params = AFTER_RETRY.run { Params("moves two", extra = proven(extraProof) { extraMoved }) }
        uow().tryPerform(TestPrincipal, AFTER_RETRY.guardedOn(proof(employee), params))
        extraProof.decidedAgain shouldBe listOf(extraMoved)
        val unlent = proof(extra)
        shouldThrow<IllegalStateException> {
            AFTER_RETRY.run { proven(unlent) { extraMoved } }.decided()
        }.message shouldBe "A proof for test.write is decided again only after an attempt lent it"
    }

    test("refuses a proven model the block writes without reading it through its proof, and leaves it unspent") {
        val extra = employee(department = home.id())
        val extraProof = proof(extra)
        val params = INITIAL.run { Params("moves two", extra = proven(extraProof) { extra }, how = UNDECIDED) }
        val guarded = INITIAL.guardedOn(proof(employee), params)
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, guarded) }
            .message shouldBe "A guarded unit of work updates only its target ${employee.id().stringValue()}, " +
            "not ${extra.id().stringValue()}, which is neither proven nor declared"
        extraProof.lent shouldBe false
    }

    test("refuses an unread proven model where the unit of work declares another aggregate") {
        val extra = employee(department = home.id())
        val extraProof = proof(extra)
        val params = INITIAL.run { Params("moves two", extra = proven(extraProof) { extra }, how = UNDECIDED) }
        val guarded = INITIAL.guardedOn(proof(employee), params)
        shouldThrow<IllegalStateException> { uow(setOf(Department::class)).tryPerform(TestPrincipal, guarded) }
            .message shouldBe "A guarded unit of work updates only its target ${employee.id().stringValue()}, " +
            "not ${extra.id().stringValue()}, which is neither proven nor declared"
    }

    test("refuses another model written in a proven model's place, though the block read the proof") {
        val extra = employee(department = home.id())
        val decoy = employee(department = home.id())
        val extraProof = proof(extra)
        val params = INITIAL.run { Params("decoys", extra = proven(extraProof) { extra }, how = DECOY, decoy = decoy) }
        val guarded = INITIAL.guardedOn(proof(employee), params)
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, guarded) }
            .message shouldBe "A guarded unit of work updates only its target ${employee.id().stringValue()}, " +
            "not ${decoy.id().stringValue()}, which is neither proven nor declared"
    }

    test("refuses a proven model an earlier attempt read through its proof, on an attempt that did not read it") {
        val extra = employee(department = home.id())
        val extraProof = proof(extra)
        val first = INITIAL.run { Params("moves two", extra = proven(extraProof) { extra }, how = PROVEN) }
        uow().tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), first))
        val retry = AFTER_RETRY.run { Params("moves two", extra = proven(extraProof) { extra }, how = UNDECIDED) }
        shouldThrow<IllegalStateException> {
            uow().tryPerform(TestPrincipal, AFTER_RETRY.guardedOn(proof(employee), retry))
        }.message shouldBe "A guarded unit of work updates only its target ${employee.id().stringValue()}, " +
            "not ${extra.id().stringValue()}, which is neither proven nor declared"
    }

    test("refuses to read a proven model whose proof was minted for another principal") {
        val extra = employee(department = home.id())
        val extraProof = TestProof(OtherPrincipal, extra) { }
        val params = INITIAL.run { Params("moves two", extra = proven(extraProof) { extra }) }
        val guarded = INITIAL.guardedOn(proof(employee), params)
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, guarded) }
            .message shouldBe "A proof for test.write on ${extra.id().stringValue()} was minted for another principal"
        extraProof.lent shouldBe false
    }

    test("updates a model of a declared aggregate without a proof, and refuses to declare a guarded one") {
        val renamed = department("Old")
        val guarded = INITIAL.guardedOn(proof(employee), Params("renames", renames = renamed))
        shouldThrow<IllegalStateException> { uow().tryPerform(TestPrincipal, guarded) }
        val changes = uow(setOf(Department::class)).tryPerform(TestPrincipal, guarded)
        changes.modelChangesToPersist.map { it.id } shouldContainExactly listOf(renamed.id(), employee.id())
        shouldThrow<IllegalArgumentException> { uow(setOf(Employee::class)) }
            .message shouldBe "MoveEmployeeUow writes Employee without a proof, and that reaches a guarded model"
        shouldThrow<IllegalArgumentException> { uow(setOf(Model::class)) }
            .message shouldBe "MoveEmployeeUow writes Model without a proof, and that reaches a guarded model"
    }

    test("composes a child that writes an unguarded model, and refuses one that writes a guarded model") {
        val other = employee(department = home.id())
        val changes = composing { RenameDepartmentUow(it, home) }
            .tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), ComposingGuardedUow.Params))
        changes.modelChangesToPersist.map { it.id } shouldContainExactly listOf(home.id(), employee.id())
        shouldThrow<IllegalStateException> {
            composing { MoveOtherEmployeeUow(it, other, away) }
                .tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), ComposingGuardedUow.Params))
        }.message shouldBe refusedWrite("MoveOtherEmployeeUow", other.id().stringValue())
    }

    test("refuses a child that writes the target itself, which only the block's update covers") {
        shouldThrow<IllegalStateException> {
            composing { MoveOtherEmployeeUow(it, employee, away) }
                .tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), ComposingGuardedUow.Params))
        }.message shouldBe refusedWrite("MoveOtherEmployeeUow", employee.id().stringValue())
    }

    test("refuses a child that holds a guarded model beside the target as unchanged, a version check without a proof") {
        val other = employee(department = home.id())
        shouldThrow<IllegalStateException> {
            composing { HoldEmployeeUow(it, other) }
                .tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), ComposingGuardedUow.Params))
        }.message shouldBe refusedWrite("HoldEmployeeUow", other.id().stringValue())
    }

    test("lands a composed child's writes after the target the block updated first") {
        val changes = targetFirst { ctx, _ -> RenameDepartmentUow(ctx, home) }
            .tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), TargetFirstGuardedUow.Params))
        changes.modelChangesToPersist.map { it.id } shouldContainExactly listOf(employee.id(), home.id())
    }

    test("refuses a child that merges onto the target instance the block updated first") {
        shouldThrow<IllegalStateException> {
            targetFirst { ctx, movedTarget -> MoveOtherEmployeeUow(ctx, movedTarget, elsewhere) }
                .tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), TargetFirstGuardedUow.Params))
        }.message shouldBe refusedWrite("MoveOtherEmployeeUow", employee.id().stringValue())
    }

    test("refuses a child whose hand-built changes drop the target the block updated first") {
        shouldThrow<IllegalStateException> {
            targetFirst { ctx, _ -> HandBuiltChildUow(ctx, home) }
                .tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), TargetFirstGuardedUow.Params))
        }.message shouldBe "Composed unit of work [HandBuiltChildUow] dropped models " +
            "[${employee.id().stringValue()}] its parent had registered. Build its changes with changes { } over " +
            "the ExecutionContext its factory is given."
    }

    test("a swallowed refusal lands the block's own write and nothing of the child's") {
        val other = employee(department = home.id())
        val moving = { ctx: ExecutionContext -> MoveOtherEmployeeUow(ctx, other, away) }
        val changes = ComposingGuardedUow(executionContext, away, moving, swallowing = true)
            .tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), ComposingGuardedUow.Params))
        changes.modelChangesToPersist.map { it.id } shouldBe listOf(employee.id())
        changes.modelChangesToPersist.none { it is NoopModel } shouldBe true
    }

    test("refuses a child that adds a guarded model") {
        val hired = newEmployee(Name("Eve", "Green"), home.id(), "eve@test.com", BUBALEH)
        shouldThrow<IllegalStateException> {
            composing(plainChild("Hire") { add(hired); "hired" })
                .tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), ComposingGuardedUow.Params))
        }.message shouldBe refusedWrite("Hire", hired.id().stringValue())
    }

    test("refuses a plain child that relays a grandchild's write of a guarded model, naming the child") {
        val other = employee(department = home.id())
        val grandchild = { ctx: ExecutionContext -> MoveOtherEmployeeUow(ctx, other, away) }
        shouldThrow<IllegalStateException> {
            composing(plainChild("Relay") { execute(grandchild, TestPrincipal) { ChildParams }; "relayed" })
                .tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), ComposingGuardedUow.Params))
        }.message shouldBe refusedWrite("Relay", other.id().stringValue())
    }

    test("after a swallowed refusal a later child merges onto the block's models and entity changes as they were") {
        val other = employee(department = home.id())
        val tag = Tag.environmentTag(home.id().id, "production")
        var refusal: Throwable? = null
        val changes = block {
            execute(plainChild("Tag") { add(tag); "tagged" }, TestPrincipal) { ChildParams }
            refusal = runCatching {
                execute({ MoveOtherEmployeeUow(it, other, away) }, TestPrincipal) { ChildParams }
            }.exceptionOrNull()
            execute({ RenameDepartmentUow(it, home) }, TestPrincipal) { ChildParams }
            update(target.changeDepartment(away))
        }.tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), ComposingGuardedUow.Params))
        refusal?.message shouldBe refusedWrite("MoveOtherEmployeeUow", other.id().stringValue())
        changes.modelChangesToPersist.map { it.id } shouldBe listOf(home.id(), employee.id())
        changes.entityChangesToPersist shouldBe listOf(AddEntity(tag))
    }

    test("does not count the owned models of an aggregate the block added as a child's writes") {
        val dept = newDeptAggregate(name = "Dept", boss = randomEmployeeId(), ration = BUBALEH).let { d ->
            d.addEmployee(newEmployee(Name("Eve", "Green"), d.id(), "eve@test.com", BUBALEH))
        }
        val changes = block {
            add(dept)
            execute({ RenameDepartmentUow(it, home) }, TestPrincipal) { ChildParams }
            update(target.changeDepartment(away))
        }.tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), ComposingGuardedUow.Params))
        changes.modelChangesToPersist.map { it.id } shouldBe
            listOf(dept.id(), dept.employees.single().id(), home.id(), employee.id())
    }

    test("refuses a child that adds an unguarded aggregate owning a new guarded model, naming the owned model") {
        val hired = newEmployee(Name("Eve", "Green"), home.id(), "eve@test.com", BUBALEH)
        val dept = newDeptAggregate(name = "Dept", boss = randomEmployeeId(), ration = BUBALEH).addEmployee(hired)
        shouldThrow<IllegalStateException> {
            block {
                execute(plainChild("Found") { add(dept); "founded" }, TestPrincipal) { ChildParams }
                update(target.changeDepartment(away))
            }.tryPerform(TestPrincipal, INITIAL.guardedOn(proof(employee), ComposingGuardedUow.Params))
        }.message shouldBe refusedWrite("Found", hired.id().stringValue())
    }

    test("answers a failed write with the target the attempt ran on, or rethrows, and none before the attempt ran") {
        val uow = uow()
        val guarded = AFTER_RETRY.guardedOn(proof(employee), Params("moves"), moved)
        val stale = StaleRecordException(setOf(employee.id()), "employee")
        shouldThrow<IllegalStateException> { uow.onFailure(guarded, stale) }
            .message shouldBe "MoveEmployeeUow: a failed write is answered only after its attempt ran"
        uow.tryPerform(TestPrincipal, guarded)
        shouldThrow<StaleRecordException> { uow.onFailure(guarded, stale) } shouldBeSameInstanceAs stale
        val answering = object : GuardedUnitOfWork<TestPrincipal, TestProof<Employee>, Params, Employee, Employee>(
            executionContext, Configuration.default(), EmployeesGuarded, setOf(),
        ) {
            override suspend fun perform(principal: TestPrincipal, params: Params) = changes { update(target) }
            override suspend fun onFailure(target: Employee, params: Params, ex: PersistenceException) = target
        }
        answering.onFailure(guarded, stale) shouldBeSameInstanceAs moved
    }

    test("a changes { } outside tryPerform is refused") {
        shouldThrow<IllegalStateException> { uow().stray() }
            .message shouldBe "MoveEmployeeUow: changes { } runs only inside tryPerform"
    }
})
