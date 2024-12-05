package com.razz.eva.uow.func

import com.razz.eva.IdempotencyKey.Companion.idempotencyKey
import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.SHAKSHOUKA
import com.razz.eva.events.IntegrationModelEvent.EventName
import com.razz.eva.events.IntegrationModelEvent.ModelId
import com.razz.eva.events.IntegrationModelEvent.ModelName
import com.razz.eva.events.IntegrationModelEvent.UowId
import com.razz.eva.events.UowEvent.UowName
import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import com.razz.eva.serialization.json.int
import com.razz.eva.serialization.json.jsonObject
import com.razz.eva.serialization.json.string
import com.razz.eva.test.schema.Tables
import com.razz.eva.uow.CreateSoloDepartmentUow
import com.razz.eva.uow.TestPrincipal
import io.kotest.matchers.shouldBe
import java.util.UUID.randomUUID
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonPrimitive

class PersistenceSpec : PersistenceBaseSpec({

    Given("Params for uow") {
        val idempotencyKey = idempotencyKey(randomUUID())
        val params = CreateSoloDepartmentUow.Params(
            bossName = Name("Misha", "K"),
            bossEmail = "misha@razz.team",
            departmentName = "mobileboys",
            ration = SHAKSHOUKA,
            idempotencyKey = idempotencyKey
        )
        lateinit var boss: Employee

        When("Principal performs uow with span") {
            val mobileboys = module.uowx.execute(CreateSoloDepartmentUow::class, TestPrincipal) { params }

            Then("New models persisted") {
                boss = module.employeeRepo.findByDepartment(mobileboys.id()).single()

                boss.name shouldBe Name("Misha", "K")
                boss.departmentId shouldBe mobileboys.id()
                mobileboys.boss shouldBe boss.id()
                mobileboys.headcount shouldBe 1
                mobileboys.name shouldBe "mobileboys"
                mobileboys.ration shouldBe SHAKSHOUKA
                boss.ration shouldBe mobileboys.ration

                val persistedBoys = module.departmentRepo.findByName("mobileboys") as OwnedDepartment

                persistedBoys.isPersisted() shouldBe true
                persistedBoys.version() shouldBe mobileboys.version()
                persistedBoys.id() shouldBe mobileboys.id()
                persistedBoys.boss shouldBe mobileboys.boss
                persistedBoys.headcount shouldBe mobileboys.headcount
                persistedBoys.name shouldBe mobileboys.name
                persistedBoys.ration shouldBe mobileboys.ration
            }

            And("Events persisted") {
                val uowEvent = module.eventQueries.getUowEvent(idempotencyKey)

                uowEvent.uowName shouldBe UowName("CreateSoloDepartmentUow")
                uowEvent.occurredAt shouldBe module.now
                uowEvent.principalId shouldBe "THIS_IS_SINGLETON"
                uowEvent.principalName shouldBe "TEST_PRINCIPAL"
                uowEvent.params["bossName"] shouldBe parseToJsonElement("""{"last":"K","first":"Misha"}""")
                uowEvent.params["bossEmail"] shouldBe JsonPrimitive("misha@razz.team")
                uowEvent.params["departmentName"] shouldBe JsonPrimitive("mobileboys")
                uowEvent.params["ration"] shouldBe JsonPrimitive("SHAKSHOUKA")
                uowEvent.params["idempotencyKey"] shouldBe JsonPrimitive(idempotencyKey.stringValue())

                uowEvent.modelEvents.size shouldBe 2
                with(uowEvent.modelEvents[0].first) {
                    payload.string("name") shouldBe mobileboys.name
                    payload.string("boss") shouldBe boss.id().id.toString()
                    payload.int("headcount") shouldBe 1
                    payload.string("ration") shouldBe mobileboys.ration.name
                    eventName shouldBe EventName("OwnedDepartmentCreated")
                    modelName shouldBe ModelName("Department")
                    modelId shouldBe ModelId(mobileboys.id().stringValue())
                    uowId shouldBe UowId(uowEvent.id.uuidValue())
                    occurredAt shouldBe uowEvent.occurredAt
                }
                with(uowEvent.modelEvents[1].first) {
                    payload.string("employeeId") shouldBe boss.id().id.toString()
                    payload.jsonObject("name").string("first") shouldBe boss.name.first
                    payload.jsonObject("name").string("last") shouldBe boss.name.last
                    payload.string("departmentId") shouldBe boss.departmentId.id.toString()
                    payload.string("email") shouldBe boss.email
                    payload.string("ration") shouldBe boss.ration.name
                    eventName shouldBe EventName("EmployeeCreated")
                    modelName shouldBe ModelName("Employee")
                    modelId shouldBe ModelId(boss.id().stringValue())
                    uowId shouldBe UowId(uowEvent.id.uuidValue())
                    occurredAt shouldBe uowEvent.occurredAt
                }
            }
        }

        When("Principal performs delete on query executor") {
            module.transactionManager.inTransaction(REQUIRE_NEW) { _ ->
                module.queryExecutor.executeQuery(
                    module.dslContext,
                    module.dslContext.deleteQuery(Tables.EMPLOYEES).apply {
                        addConditions(Tables.EMPLOYEES.ID.eq(boss.id().id))
                    },
                )
            }

            Then("New model persisted") {
                val deletedBoss = module.employeeRepo.find(boss.id())
                deletedBoss shouldBe null
            }
        }
    }
})
