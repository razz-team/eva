package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.Department.OrphanedDepartment
import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent.NameChanged
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UpdateModel
import com.razz.eva.uow.firstAttempt
import com.razz.eva.uow.guarded.Fixtures.department
import com.razz.eva.uow.guarded.Proven.Companion.proven
import com.razz.eva.uow.guarded.RenameOrphanUow.Params
import com.razz.eva.uow.retry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.opentelemetry.api.OpenTelemetry

/** A proof whose attempts write a narrower type than its reload loads, and what eva refuses of its answers. */
class NarrowingProofSpec : FunSpec({

    val noAttempt = ExecutionContext(fixedUTC(millisUTC().instant()), OpenTelemetry.noop())
    val rename = { ctx: ExecutionContext -> RenameOrphanUow(ctx) }
    val target = department("Home")
    fun stored(): OrphanedDepartment = OrphanedDepartment(target.id(), "Home", 3, BUBALEH, persistentState(V1, null))
    val owned: Department<*> =
        OwnedDepartment(target.id(), "Home", EmployeeId(), 3, BUBALEH, persistentState(V1, null))
    val orphansOnly: (Department<*>) -> OrphanedDepartment = {
        when (it) {
            is OrphanedDepartment -> it
            is OwnedDepartment -> throw Refused("has a boss now")
        }
    }

    test("writes the proof's target on the first attempt, as the narrow type") {
        val proof = OrphanProof(target, orphansOnly)
        val changes = rename.firstAttempt(noAttempt, TestPrincipal) {
            Params(proven(proof) { error("the first attempt does not reload") }, "Renamed")
        }
        changes.result.name shouldBe "Renamed"
        proof.askedAgain shouldBe 0
    }

    test("writes on a retry the reloaded model the proof narrowed, that same instance") {
        val proof = OrphanProof(target, orphansOnly)
        val reloaded = stored()
        val wide: Department<*> = reloaded
        val changes = rename.retry(noAttempt, TestPrincipal) { Params(proven(proof) { wide }, "Renamed") }
        val change = changes.modelChangesToPersist.single().shouldBeInstanceOf<UpdateModel<*, *>>()
        change.modelEvents shouldBe listOf(NameChanged(target.id(), "Home", "Renamed"))
        proof.askedAgain shouldBe 1
    }

    test("a refusal by checkAgain on a retry is thrown from model(), and nothing is written") {
        val proof = OrphanProof(target, orphansOnly)
        shouldThrow<Refused> {
            rename.retry(noAttempt, TestPrincipal) { Params(proven(proof) { owned }, "Renamed") }
        }.message shouldBe "has a boss now"
    }

    test("refuses a checkAgain that returns its target, equal to the reload and another instance") {
        val proof = OrphanProof(target) { target }
        val reloaded = stored()
        reloaded shouldBe target
        shouldThrow<IllegalStateException> {
            rename.retry(noAttempt, TestPrincipal) { Params(proven(proof) { reloaded }, "Renamed") }
        }.message shouldBe "checkAgain of the proof for test.write returned another instance than the model eva " +
            "reloaded for the retry. Return the reloaded model itself"
    }

    test("refuses a reload with unsaved changes before the proof is asked") {
        val proof = OrphanProof(target, orphansOnly)
        val unsaved = stored().rename("Unsaved")
        shouldThrow<IllegalStateException> {
            rename.retry(noAttempt, TestPrincipal) { Params(proven(proof) { unsaved }, "Renamed") }
        }.message shouldBe "The reload of a Proven returned ${target.id().stringValue()} with unsaved changes. " +
            "The reload must return the model as it is stored"
        proof.askedAgain shouldBe 0
    }
})
