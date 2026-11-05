package com.razz.eva.uow.guarded

import com.razz.eva.uow.InstantiationContextFixtures.AFTER_RETRY
import com.razz.eva.uow.InstantiationContextFixtures.INITIAL
import com.razz.eva.uow.guarded.Fixtures.employee
import com.razz.eva.uow.guarded.Fixtures.proof
import com.razz.eva.uow.guarded.Guarded.Companion.guarded
import com.razz.eva.uow.guarded.Proven.Companion.proven
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

/** A proof bound into another unit of work's params: spent on its one read, and never shared with a run. */
class ProvenSpec : FunSpec({

    val employee = employee()
    val reloaded = employee(id = employee.id(), department = employee.departmentId)

    test("keeps the model the proof was decided on in the first attempt, and lends the proof on the first read") {
        val proof = proof(employee)
        val proven = INITIAL.proven(proof) { error("the first attempt does not reload") }
        proven.decided() shouldBeSameInstanceAs employee
        proven.decided() shouldBeSameInstanceAs employee
        proof.lent shouldBe true
    }

    test("refuses a retry on a proof that ran its own unit of work") {
        val proof = proof(employee)
        INITIAL.guarded(proof, MoveEmployeeUow.Params("ran")) { employee }
        shouldThrow<IllegalStateException> { AFTER_RETRY.proven(proof) { reloaded }.decided() }
            .message shouldBe "A proof for test.write is decided again only after an attempt lent it"
    }

    test("lends a proof once, so two params fields cannot carry one decision") {
        val proof = proof(employee)
        INITIAL.proven(proof) { reloaded }.decided()
        shouldThrow<IllegalStateException> { INITIAL.proven(proof) { reloaded }.decided() }
            .message shouldBe "A proof for test.write covers one write, and this one ran"
    }

    test("a lent proof runs no unit of work, and a proof that ran is not lent") {
        val lent = proof(employee)
        INITIAL.proven(lent) { reloaded }.decided()
        shouldThrow<IllegalStateException> { INITIAL.guarded(lent, MoveEmployeeUow.Params("lent")) { employee } }
            .message shouldBe "A proof for test.write runs once, and this one ran"
        val ran = proof(employee)
        INITIAL.guarded(ran, MoveEmployeeUow.Params("ran")) { employee }
        shouldThrow<IllegalStateException> { INITIAL.proven(ran) { reloaded }.decided() }
            .message shouldBe "A proof for test.write covers one write, and this one ran"
    }

    test("writes its action and its model's id for the audit row, and nothing else") {
        val proof = proof(employee)
        val proven = INITIAL.proven(proof) { reloaded }
        proven.action shouldBe "test.write"
        proven.targetId shouldBe employee.id().stringValue()
    }
})
