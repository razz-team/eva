package com.razz.eva.uow.guarded

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

/** A proof bound into params: spent as the first attempt binds it, decided again on each retry's reload. */
class ProvenSpec : FunSpec({

    val employee = employee()
    val reloaded = employee(id = employee.id(), department = employee.departmentId)
    val first = initial(TestPrincipal)
    val retry = afterRetry(TestPrincipal)

    test("keeps the model the proof was decided on in the first attempt, and decides it once") {
        val proven = first.proven(proof(employee)) { error("the first attempt does not reload") }
        proven.decidedOrNull() shouldBe null
        proven.decided() shouldBeSameInstanceAs employee
        proven.decided() shouldBeSameInstanceAs employee
        proven.decidedOrNull() shouldBeSameInstanceAs employee
    }

    test("decides a retry's reload once, however often the block reads it") {
        val proof = proof(employee)
        first.proven(proof) { error("the first attempt does not reload") }
        val proven = retry.proven(proof) { reloaded }
        proven.decided() shouldBeSameInstanceAs reloaded
        proven.decided() shouldBeSameInstanceAs reloaded
        proof.decidedAgain shouldBe listOf(reloaded)
    }

    test("refuses a retry whose reload answers another model, and decides nothing again") {
        val proof = proof(employee)
        first.proven(proof) { error("the first attempt does not reload") }
        val other = employee(department = employee.departmentId)
        shouldThrow<IllegalStateException> { retry.proven(proof) { other }.decided() }
            .message shouldBe
            "The reload answered ${other.id().stringValue()} for the proof's ${employee.id().stringValue()}"
        proof.decidedAgain shouldBe listOf()
    }

    test("spends a proof once, so two params fields cannot carry one decision") {
        val proof = proof(employee)
        first.proven(proof) { reloaded }
        shouldThrow<IllegalStateException> { first.proven(proof) { reloaded } }
            .message shouldBe "A proof for test.write is spent once, and this one was"
    }

    test("spends a proof as the params bind it, whether or not the block reads it") {
        val proof = proof(employee)
        first.proven(proof) { reloaded }
        retry.proven(proof) { reloaded }
        proof.decidedAgain shouldBe listOf()
    }

    test("writes its action and its model's id for the audit row, and nothing else") {
        val proven = first.proven(proof(employee)) { reloaded }
        proven.action shouldBe "test.write"
        proven.targetId shouldBe employee.id().stringValue()
    }
})
