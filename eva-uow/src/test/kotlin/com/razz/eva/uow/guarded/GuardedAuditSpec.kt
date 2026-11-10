package com.razz.eva.uow.guarded

import com.razz.eva.uow.InstantiationContextFixtures.initial
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.guarded.Fixtures.employee
import com.razz.eva.uow.guarded.Fixtures.proof
import com.razz.eva.uow.guarded.Proven.Companion.proven
import com.razz.eva.uow.params.kotlinx.KotlinxParamsSerializer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GuardedAuditSpec : FunSpec({

    val first = initial(TestPrincipal)

    test("a guarded params row is the unit of work's own params, each proven field its action and model id") {
        val employee = employee()
        val extra = employee()
        val params = first.run {
            MoveEmployeeUow.Params(
                proven(proof(employee)) { employee },
                "moves",
                extra = proven(proof(extra)) { extra },
                how = MoveEmployeeUow.How.UNREAD,
            )
        }
        KotlinxParamsSerializer().serialize(params) shouldBe
            """{"employee":{"action":"test.write","target":"${employee.id().stringValue()}"},"note":"moves",""" +
            """"extra":{"action":"test.write","target":"${extra.id().stringValue()}"},"how":"UNREAD"}"""
    }

    test("a list of proven fields writes one entry per proof, in order") {
        val team = listOf(employee(), employee())
        val params = first.run { TeamParams(team.map { member -> proven(proof(member)) { member } }) }
        KotlinxParamsSerializer().serialize(params) shouldBe
            """{"team":[{"action":"test.write","target":"${team[0].id().stringValue()}"},""" +
            """{"action":"test.write","target":"${team[1].id().stringValue()}"}]}"""
    }
})
