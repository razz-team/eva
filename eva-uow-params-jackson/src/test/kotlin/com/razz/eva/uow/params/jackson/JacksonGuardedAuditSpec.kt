package com.razz.eva.uow.params.jackson

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Name
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

/** Jackson writes a Proven field as the kotlinx serializer does: its action and its model id. */
class JacksonGuardedAuditSpec : ShouldSpec({

    val write = object : Action {
        override val name = "test.write"
    }

    class Allowed(override val target: Employee, write: Action) : Proof<Employee>() {
        override val principal = TestPrincipal
        override val action = write
        override suspend fun checkAgain(reloaded: Employee) = Unit
    }

    data class Params(
        val employee: Proven<Allowed, Employee>,
        val note: String,
        val extra: List<Proven<Allowed, Employee>>,
    ) : UowParams<Params>

    fun employee() = Employee(
        EmployeeId(), Name("Ada", "Lovelace"), randomDepartmentId(), "ada@test.com", BUBALEH, persistentState(V1, null),
    )

    should("write each Proven field as its action and its model id, and nothing else of the proof") {
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
        mine.readTree(mine.writeValueAsString(proven)) shouldBe
            mine.readTree("""{"action":"test.write","targetId":"${target.id().stringValue()}"}""")
    }
})
