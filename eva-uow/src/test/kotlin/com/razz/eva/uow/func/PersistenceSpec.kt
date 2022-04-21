package com.razz.eva.uow.func

import com.razz.eva.IdempotencyKey.Companion.idempotencyKey
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.SHAKSHOUKA
import com.razz.eva.events.IntegrationModelEvent.EventName
import com.razz.eva.events.IntegrationModelEvent.ModelId
import com.razz.eva.events.IntegrationModelEvent.ModelName
import com.razz.eva.events.IntegrationModelEvent.UowId
import com.razz.eva.serialization.json.int
import com.razz.eva.serialization.json.jsonObject
import com.razz.eva.serialization.json.string
import com.razz.eva.tracing.Tracing.withNewSpan
import com.razz.eva.uow.CreateSoloDepartmentUow
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UowEvent.UowName
import io.kotest.matchers.shouldBe
import io.opentracing.Tracer
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapAdapter
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID.randomUUID

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

        When("Principal performs uow with span") {
            val span = { tracer: Tracer ->
                tracer.buildSpan("event-repo-spec").asChildOf(
                    tracer.extract(
                        Format.Builtin.TEXT_MAP,
                        TextMapAdapter(
                            mapOf(
                                "x-b3-spanid" to "0000000001234567",
                                "x-b3-traceid" to "0000000007654321",
                                "x-b3-sampled" to "1"
                            )
                        )
                    )
                ).start()
            }
            val mobileboys = withNewSpan(module.tracer, span) {
                module.uowx.execute(CreateSoloDepartmentUow::class, TestPrincipal) { params }
            }
            lateinit var boss: Employee

            Then("New model persisted") {
                boss = module.employeeRepo.findByDepartment(mobileboys.id()).single()

                boss.name shouldBe Name("Misha", "K")
                boss.departmentId shouldBe mobileboys.id()
                mobileboys.boss shouldBe boss.id()
                mobileboys.headcount shouldBe 1
                mobileboys.name shouldBe "mobileboys"
                mobileboys.ration shouldBe SHAKSHOUKA
                boss.ration shouldBe mobileboys.ration
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
                uowEvent.modelEvents[0].second shouldBe parseToJsonElement(
                    """
                    {
                    "X-B3-SpanId":"${module.tracer.lastSpanId}",
                    "X-B3-Sampled":"1",
                    "X-B3-TraceId":"0000000007654321",
                    "X-B3-ParentSpanId":"0000000001234567"
                    }
                    """
                )
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
                uowEvent.modelEvents[1].second shouldBe parseToJsonElement(
                    """
                    {
                    "X-B3-SpanId":"${module.tracer.lastSpanId}",
                    "X-B3-Sampled":"1",
                    "X-B3-TraceId":"0000000007654321",
                    "X-B3-ParentSpanId":"0000000001234567"
                    }
                    """
                )
            }
        }
    }
})
