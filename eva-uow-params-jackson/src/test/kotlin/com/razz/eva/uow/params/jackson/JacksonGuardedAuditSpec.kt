package com.razz.eva.uow.params.jackson

import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Principal
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.uow.InstantiationContextFixtures.INITIAL
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.guarded.Action
import com.razz.eva.uow.guarded.Guarded.Companion.guarded
import com.razz.eva.uow.guarded.Proof
import com.razz.eva.uow.guarded.Proven
import com.razz.eva.uow.guarded.Proven.Companion.proven
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

/**
 * The audit row of a guarded unit of work through Jackson: the envelope the kotlinx serializer writes, over the
 * params as the mapper writes them.
 */
class JacksonGuardedAuditSpec : ShouldSpec({

    val write = object : Action {
        override val name = "test.write"
    }

    class Decided(override val target: Employee, write: Action) : Proof<Principal<*>, Action, Employee> {
        override val principal = TestPrincipal
        override val action = write
        override suspend fun decideAgain(reloaded: Employee) = Unit
        override fun run() = Unit
        override fun lend() = Unit
        override val lent = false
    }

    data class Params(
        val note: String,
        val extra: Proven<Decided, Employee>?,
    ) : UowParams<Params>

    fun employee() = Employee(
        EmployeeId(), Name("Ada", "Lovelace"), randomDepartmentId(), "ada@test.com", BUBALEH, persistentState(V1, null),
    )

    should("write a guarded params row as the action, the target's id and the unit of work's params") {
        val target = employee()
        val guarded = INITIAL.guarded(Decided(target, write), Params("moves", extra = null)) { target }
        JacksonParamsSerializer().serialize(guarded) shouldBe
            """{"action":"test.write","target":"${target.id().stringValue()}",""" +
            """"params":{"note":"moves","extra":null,"idempotencyKey":null}}"""
    }

    should("write a proof bound into params as its action and its model's id, never the proof") {
        val target = employee()
        val extra = employee()
        val params = INITIAL.run { Params("moves two", extra = proven(Decided(extra, write)) { extra }) }
        JacksonParamsSerializer().serialize(INITIAL.guarded(Decided(target, write), params) { target }) shouldBe
            """{"action":"test.write","target":"${target.id().stringValue()}","params":{"note":"moves two",""" +
            """"extra":{"action":"test.write","target":"${extra.id().stringValue()}"},"idempotencyKey":null}}"""
    }
})
