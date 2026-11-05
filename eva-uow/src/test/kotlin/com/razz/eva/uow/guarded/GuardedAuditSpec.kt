package com.razz.eva.uow.guarded

import com.razz.eva.uow.InstantiationContextFixtures.INITIAL
import com.razz.eva.uow.guarded.Fixtures.employee
import com.razz.eva.uow.guarded.Fixtures.proof
import com.razz.eva.uow.guarded.Guarded.Companion.guarded
import com.razz.eva.uow.guarded.Proven.Companion.proven
import com.razz.eva.uow.params.kotlinx.KotlinxParamsSerializer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GuardedAuditSpec : FunSpec({

    test("a guarded params row carries the action, the target's id and the unit of work's params") {
        val target = employee()
        val extra = employee()
        val extraProof = proof(extra)
        val params = INITIAL.run {
            MoveEmployeeUow.Params("moves", extra = proven(extraProof) { extra }, how = MoveEmployeeUow.How.UNDECIDED)
        }
        val guarded = INITIAL.guarded(proof(target), params) { target }
        KotlinxParamsSerializer().serialize(guarded) shouldBe
            """{"action":"test.write","target":"${target.id().stringValue()}",""" +
            """"params":{"note":"moves","extra":{"action":"test.write","target":"${extra.id().stringValue()}"},""" +
            """"how":"UNDECIDED"}}"""
    }

    test("the principal is the row's own: a guarded params row reads the inner idempotency key") {
        val target = employee()
        val guarded = INITIAL.guarded(proof(target), MoveEmployeeUow.Params("moves")) { target }
        guarded.idempotencyKey shouldBe null
        guarded.action shouldBe "test.write"
        guarded.targetId shouldBe target.id().stringValue()
    }
})
