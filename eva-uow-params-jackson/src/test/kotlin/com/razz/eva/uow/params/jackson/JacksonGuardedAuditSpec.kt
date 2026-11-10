package com.razz.eva.uow.params.jackson

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Principal
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.uow.InstantiationContextFixtures.initial
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.guarded.Action
import com.razz.eva.uow.guarded.Proof
import com.razz.eva.uow.guarded.Proven
import com.razz.eva.uow.guarded.Proven.Companion.proven
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

/** A proof bound into params, through Jackson: the fields the kotlinx serializer writes, never the proof. */
class JacksonGuardedAuditSpec : ShouldSpec({

    val write = object : Action {
        override val name = "test.write"
    }

    class Allowed(override val target: Employee, write: Action) : Proof<Principal<*>, Action, Employee>() {
        override val principal = TestPrincipal
        override val action = write
        override suspend fun decideAgain(reloaded: Employee) = Unit
    }

    data class Params(
        val employee: Proven<Allowed, Employee>,
        val note: String,
        val extra: List<Proven<Allowed, Employee>>,
    ) : UowParams<Params>

    fun employee() = Employee(
        EmployeeId(), Name("Ada", "Lovelace"), randomDepartmentId(), "ada@test.com", BUBALEH, persistentState(V1, null),
    )

    should("write each proof bound into params as its action and its model's id, never the proof") {
        val target = employee()
        val extra = employee()
        val params = initial(TestPrincipal).run {
            Params(
                proven(Allowed(target, write)) { target },
                "moves two",
                listOf(proven(Allowed(extra, write)) { extra }),
            )
        }
        JacksonParamsSerializer().serialize(params) shouldBe
            """{"employee":{"action":"test.write","target":"${target.id().stringValue()}"},"note":"moves two",""" +
            """"extra":[{"action":"test.write","target":"${extra.id().stringValue()}"}],"idempotencyKey":null}"""
    }

    should("leave the mapper it was given as it was") {
        val target = employee()
        val mine = jacksonObjectMapper()
        JacksonParamsSerializer(mine)
        val proven = initial(TestPrincipal).run { proven(Allowed(target, write)) { target } }
        mine.writeValueAsString(proven) shouldBe
            """{"action":"test.write","targetId":"${target.id().stringValue()}"}"""
    }
})
