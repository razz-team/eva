package com.razz.eva.repository

import com.razz.eva.IdempotencyKey
import com.razz.eva.domain.DepartmentEvent.OrphanedDepartmentCreated
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.EmployeeEvent.EmployeeCreated
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration
import com.razz.eva.events.UowEvent
import com.razz.eva.events.UowEvent.ModelEventId
import com.razz.eva.events.UowEvent.UowName
import com.razz.eva.events.db.tables.ModelEvents.MODEL_EVENTS
import com.razz.eva.events.db.tables.UowEvents.UOW_EVENTS
import com.razz.eva.events.db.tables.records.ModelEventsRecord
import com.razz.eva.events.db.tables.records.UowEventsRecord
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.QueryExecuted
import com.razz.eva.serialization.json.JsonFormat.json
import com.razz.eva.tracing.testing.OpenTelemetryTestConfiguration
import com.razz.eva.tracing.textPropagation
import com.razz.eva.tracing.use
import com.razz.eva.uow.UowParams
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.opentelemetry.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import java.time.Instant.now
import java.util.UUID.randomUUID

@Serializable
data class Params(
    val id: Int,
    val name: String,
    override val idempotencyKey: IdempotencyKey
) : UowParams<Params> {
    override fun serialization() = serializer()
}

class JooqEventRepositorySpec : BehaviorSpec({
    val now = now()

    val openTelemetry = OpenTelemetryTestConfiguration.create()

    Given("SqlEventRepository with hacked queryExecutor and tracing context") {
        val dslContext = DSL.using(POSTGRES)
        val queryExecutor = FakeMemorizingQueryExecutor()
        val eventRepo = JooqEventRepository(queryExecutor, dslContext, openTelemetry)

        And("Unit of Work Event") {
            val depId = randomDepartmentId()
            val params = Params(1, "Nik", IdempotencyKey.random())
            val uowEvent = UowEvent(
                id = UowEvent.Id.random(),
                uowName = UowName("TestUow"),
                principal = TestPrincipal,
                modelEvents = listOf(
                    ModelEventId.random() to OrphanedDepartmentCreated(
                        depId,
                        "PoContrE",
                        1_337,
                        Ration.SHAKSHOUKA
                    ),
                    ModelEventId.random() to EmployeeCreated(
                        EmployeeId(randomUUID()),
                        Name("rabotyaga", "#1"),
                        depId,
                        "rabotyaga1@top_pocontre.eu",
                        Ration.SHAKSHOUKA
                    )
                ),
                idempotencyKey = params.idempotencyKey,
                params = json.encodeToString(params.serialization(), params),
                occurredAt = now
            )

            When("Principal saving Unit of Work Event with two model events") {
                val span = openTelemetry.tracerProvider.get("JooqEventRepositorySpec")
                    .spanBuilder("Test").setParent(Context.root()).startSpan()

                val traceContext = span.use {
                    eventRepo.add(uowEvent)
                    textPropagation(openTelemetry.propagators.textMapPropagator)
                }

                Then("Query executor should receive one uow event and two model events") {
                    queryExecutor.executionHistory shouldBe listOf(
                        QueryExecuted(
                            dslContext,
                            dslContext.insertQuery(UOW_EVENTS)
                                .also {
                                    it.setRecord(
                                        UowEventsRecord().apply {
                                            this.id = uowEvent.id.uuidValue()
                                            this.name = uowEvent.uowName.toString()
                                            this.idempotencyKey = uowEvent.idempotencyKey?.stringValue()
                                            this.principalName = "TEST_PRINCIPAL"
                                            this.principalId = "THIS_IS_SINGLETON"
                                            this.occurredAt = now
                                            this.modelEvents = uowEvent
                                                .modelEvents.map { (id, _) -> id.uuidValue() }.toTypedArray()
                                            this.params = parseToJsonElement(
                                                """
                                                {
                                                    "id":1,
                                                    "name":"Nik",
                                                    "idempotencyKey":"${uowEvent.idempotencyKey?.stringValue()}"
                                                }
                                                """
                                            ).toString()
                                        }
                                    )
                                },
                        ),
                        QueryExecuted(
                            dslContext,
                            dslContext.insertQuery(MODEL_EVENTS)
                                .also {
                                    it.addRecord(
                                        uowEvent.modelEvents.first().let { (key, value) ->
                                            ModelEventsRecord().apply {
                                                this.id = key.uuidValue()
                                                this.uowId = uowEvent.id.uuidValue()
                                                this.modelId = value.modelId.id.toString()
                                                this.name = value.eventName()
                                                this.modelName = value.modelName
                                                this.occurredAt = now
                                                this.payload = value.integrationEvent().toString()
                                                this.tracingContext = json.encodeToString(traceContext)
                                            }
                                        }
                                    )
                                    it.addRecord(
                                        uowEvent.modelEvents.last().let { (key, value) ->
                                            ModelEventsRecord().apply {
                                                this.id = key.uuidValue()
                                                this.uowId = uowEvent.id.uuidValue()
                                                this.modelId = value.modelId.id.toString()
                                                this.name = value.eventName()
                                                this.modelName = value.modelName
                                                this.occurredAt = now
                                                this.payload = value.integrationEvent().toString()
                                                this.tracingContext = json.encodeToString(traceContext)
                                            }
                                        }
                                    )
                                },
                        )
                    )
                }
            }
        }
    }

    Given("Another sqlEventRepository with hacked queryExecutor and tracing context") {
        val dslContext = DSL.using(POSTGRES)
        val queryExecutor = FakeMemorizingQueryExecutor()

        val eventRepo = JooqEventRepository(queryExecutor, dslContext)

        And("Unit of Work Event without model events") {
            val params = Params(1, "Nik", IdempotencyKey.random())
            val uowEvent = UowEvent(
                id = UowEvent.Id(randomUUID()),
                uowName = UowName("TestUow"),
                principal = TestPrincipal,
                modelEvents = listOf(),
                idempotencyKey = params.idempotencyKey,
                params = json.encodeToString(params.serialization(), params),
                occurredAt = now
            )

            When("Principal saving Unit of Work Event") {
                eventRepo.add(uowEvent)

                Then("Query executor should receive one uow event") {
                    queryExecutor.executionHistory shouldBe listOf(
                        QueryExecuted(
                            dslContext,
                            dslContext.insertQuery(UOW_EVENTS)
                                .also {
                                    it.setRecord(
                                        UowEventsRecord().apply {
                                            this.id = uowEvent.id.uuidValue()
                                            this.name = uowEvent.uowName.toString()
                                            this.idempotencyKey = uowEvent.idempotencyKey?.stringValue()
                                            this.principalName = "TEST_PRINCIPAL"
                                            this.principalId = "THIS_IS_SINGLETON"
                                            this.occurredAt = now
                                            this.modelEvents = uowEvent
                                                .modelEvents.map { (id, _) -> id.uuidValue() }.toTypedArray()
                                            this.params = parseToJsonElement(
                                                """
                                                {
                                                    "id":1,
                                                    "name":"Nik",
                                                    "idempotencyKey":"${uowEvent.idempotencyKey?.stringValue()}"
                                                }
                                                """
                                            ).toString()
                                        }
                                    )
                                },
                        )
                    )
                }
            }
        }
    }

    Given("Another sqlEventRepository with hacked queryExecutor and no tracing context") {
        val dslContext = DSL.using(POSTGRES)
        val queryExecutor = FakeMemorizingQueryExecutor()

        val eventRepo = JooqEventRepository(queryExecutor, dslContext)

        And("Unit of Work Event without model events") {
            val params = Params(1, "Nik", IdempotencyKey.random())
            val uowEvent = UowEvent(
                id = UowEvent.Id(randomUUID()),
                uowName = UowName("TestUow"),
                principal = TestPrincipal,
                modelEvents = listOf(),
                idempotencyKey = params.idempotencyKey,
                params = json.encodeToString(params.serialization(), params),
                occurredAt = now
            )

            When("Principal saving Unit of Work Event") {
                eventRepo.add(uowEvent)

                Then("Query executor should receive one uow event") {
                    queryExecutor.executionHistory shouldBe listOf(
                        QueryExecuted(
                            dslContext,
                            dslContext.insertQuery(UOW_EVENTS)
                                .also {
                                    it.setRecord(
                                        UowEventsRecord().apply {
                                            id = uowEvent.id.uuidValue()
                                            name = uowEvent.uowName.toString()
                                            idempotencyKey = uowEvent.idempotencyKey?.stringValue()
                                            principalName = "TEST_PRINCIPAL"
                                            principalId = "THIS_IS_SINGLETON"
                                            occurredAt = now
                                            modelEvents = uowEvent
                                                .modelEvents.map { (id, _) -> id.uuidValue() }.toTypedArray()
                                            this.params = parseToJsonElement(
                                                """
                                                {
                                                    "id":1,
                                                    "name":"Nik",
                                                    "idempotencyKey":"${uowEvent.idempotencyKey?.stringValue()}"
                                                }
                                                """
                                            ).toString()
                                        }
                                    )
                                },
                        )
                    )
                }
            }
        }
    }
})
