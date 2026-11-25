package com.razz.eva.uow.guarded

import com.razz.eva.domain.Employee
import com.razz.eva.domain.Principal
import com.razz.eva.uow.InstantiationContextFixtures.afterRetry
import com.razz.eva.uow.InstantiationContextFixtures.initial
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.guarded.Fixtures.employee
import com.razz.eva.uow.guarded.Fixtures.proof
import com.razz.eva.uow.guarded.Proven.Companion.proven
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.serialization.json.Json

/** A proof wrapped in a Proven: the first attempt marks it as used, and each retry asks it about the reload. */
class ProvenSpec : FunSpec({

    val employee = employee()
    val reloaded = employee(id = employee.id(), department = employee.departmentId)
    val first = initial(TestPrincipal)
    val retry = afterRetry(first)

    test("returns the proof's target on the first attempt, the same instance on every read") {
        val proven = first.proven(proof(employee)) { error("the first attempt does not reload") }
        proven.readOrNull() shouldBe null
        proven.read() shouldBeSameInstanceAs employee
        proven.read() shouldBeSameInstanceAs employee
        proven.readOrNull() shouldBeSameInstanceAs employee
    }

    // A target getter that answers another model once proven() read it.
    class FickleProof(private val checked: Employee, private val later: Employee) : Proof<Employee, Employee>() {
        private var reads = 0
        override val principal = TestPrincipal
        override val action = Write
        override val target: Employee get() = if (reads++ == 0) checked else later
        override suspend fun checkAgain(reloaded: Employee) = reloaded
    }

    test("returns on the first attempt the target proven() checked, however the proof answers it later") {
        val other = employee(department = employee.departmentId)
        val proven = first.proven(FickleProof(employee, other)) { error("the first attempt does not reload") }
        proven.read() shouldBeSameInstanceAs employee
        proven.targetId shouldBe employee.id().stringValue()
    }

    test("asks the proof about a retry's reload once, however often the block reads it") {
        val proof = proof(employee)
        first.proven(proof) { error("the first attempt does not reload") }
        val proven = retry.proven(proof) { reloaded }
        proven.read() shouldBeSameInstanceAs reloaded
        proven.read() shouldBeSameInstanceAs reloaded
        proof.checkedAgain shouldBe listOf(reloaded)
    }

    test("refuses a retry whose reload returns another model, and does not ask the proof") {
        val proof = proof(employee)
        first.proven(proof) { error("the first attempt does not reload") }
        val other = employee(department = employee.departmentId)
        shouldThrow<IllegalStateException> { retry.proven(proof) { other }.read() }
            .message shouldBe
            "The reload of a Proven returned ${other.id().stringValue()}, and its proof is for " +
            "${employee.id().stringValue()}. The reload must load the proof's target by its id"
        proof.checkedAgain shouldBe listOf()
    }

    test("marks a proof as used once, so two params fields cannot carry one proof") {
        val proof = proof(employee)
        first.proven(proof) { reloaded }
        shouldThrow<IllegalStateException> { first.proven(proof) { reloaded } }
            .message shouldBe "This proof for test.write was already used to run a unit of work. One proof runs " +
            "one unit of work: get a new proof for another run"
    }

    test("marks a proof as used when the params wrap it, whether or not the block reads it") {
        val proof = proof(employee)
        first.proven(proof) { reloaded }
        retry.proven(proof) { reloaded }
        proof.checkedAgain shouldBe listOf()
    }

    // An action whose name answers another action once proven() read it.
    class FickleAction : Action {
        private var reads = 0
        override val name: String get() = if (reads++ == 0) "test.write" else "test.other"
    }

    fun fickleActionProof(issuedFor: Principal<*>) = object : Proof<Employee, Employee>() {
        override val principal = issuedFor
        override val action = FickleAction()
        override val target = employee
        override suspend fun checkAgain(reloaded: Employee) = reloaded
    }

    test("writes for the audit row the action proven() read, however the proof answers it later") {
        val proven = first.proven(fickleActionProof(TestPrincipal)) { reloaded }
        proven.action shouldBe "test.write"
        Json.encodeToString(Proven.Audit, proven) shouldBe
            "{\"action\":\"test.write\",\"target\":\"${employee.id().stringValue()}\"}"
    }

    test("names in a refusal the action proven() read, however the proof answers it later") {
        shouldThrow<IllegalStateException> { first.proven(fickleActionProof(OtherPrincipal)) { reloaded } }
            .message shouldBe "The proof for test.write on ${employee.id().stringValue()} was issued for another " +
            "principal than the one this unit of work runs under. Run it under the proof's principal"
    }

    test("writes its action and its model's id for the audit row, and nothing else") {
        val proven = first.proven(proof(employee)) { reloaded }
        proven.action shouldBe "test.write"
        proven.targetId shouldBe employee.id().stringValue()
    }
})
