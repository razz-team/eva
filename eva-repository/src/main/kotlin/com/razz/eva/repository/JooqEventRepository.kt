package com.razz.eva.repository

import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelWithPrincipalEvent
import com.razz.eva.domain.Principal
import com.razz.eva.events.UowEvent
import com.razz.eva.events.UowEvent.ModelEventId
import com.razz.eva.events.db.tables.ModelEvents.MODEL_EVENTS
import com.razz.eva.events.db.tables.UowEvents.UOW_EVENTS
import com.razz.eva.events.db.tables.records.ModelEventsRecord
import com.razz.eva.events.db.tables.records.UowEventsRecord
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.persistence.PersistenceException.UniqueUowEventRecordViolationException
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.repository.Fake.FakeModelEvent
import com.razz.eva.repository.PgHelpers.PG_UNIQUE_VIOLATION
import com.razz.eva.repository.PgHelpers.extractUniqueConstraintName
import com.razz.eva.serialization.json.JsonFormat.json
import com.razz.eva.tracing.contextMap
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import io.vertx.pgclient.PgException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.encodeToString
import org.jooq.DSLContext
import org.jooq.InsertQuery
import org.jooq.Record
import org.jooq.exception.DataAccessException

class JooqEventRepository(
    private val queryExecutor: QueryExecutor,
    private val dslContext: DSLContext,
    private val openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
    private val maxEventPayloadSize: Int = 1024 * 1024,
) : EventRepository {

    override suspend fun warmup() {
        toUERecord(Fake.uowEvent)
        toMERecord(
            uowEvent = Fake.uowEvent,
            eventId = FakeModelEvent.eventId,
            modelEvent = FakeModelEvent
        )
        val select = dslContext.selectFrom(MODEL_EVENTS)
            .orderBy(MODEL_EVENTS.INSERTED_AT.desc())
            .limit(1)
        queryExecutor.executeSelect(
            dslContext = dslContext,
            jooqQuery = select,
            table = select.asTable(),
        ).firstOrNull()
    }

    private fun toMERecord(
        uowEvent: UowEvent,
        eventId: ModelEventId,
        modelEvent: ModelEvent<*>
    ): ModelEventsRecord {
        val payloadString = modelEvent.payload(uowEvent.principal).toString()
        val payloadSize = payloadString.utf8SizeInBytes()

        if (payloadSize > maxEventPayloadSize) {
            throw PersistenceException.EventPayloadTooLargeException(
                modelId = modelEvent.modelId,
                modelEventId = eventId.uuidValue(),
                eventId = uowEvent.id.uuidValue(),
                payloadSize = payloadSize,
                maxEventPayloadSize = maxEventPayloadSize,
            )
        }

        return ModelEventsRecord().apply {
            id = eventId.uuidValue()
            uowId = uowEvent.id.uuidValue()
            modelId = modelEvent.modelId.stringValue()
            name = modelEvent.eventName()
            modelName = modelEvent.modelName
            occurredAt = uowEvent.occurredAt
            payload = payloadString
        }
    }

    private fun toUERecord(uowEvent: UowEvent): UowEventsRecord {
        return UowEventsRecord().apply {
            id = uowEvent.id.uuidValue()
            name = uowEvent.uowName.toString()
            idempotencyKey = uowEvent.idempotencyKey?.stringValue()
            principalId = uowEvent.principal.id.toString()
            principalName = uowEvent.principal.name.toString()
            occurredAt = uowEvent.occurredAt
            modelEvents = uowEvent.modelEvents
                .map { (id, _) -> id.uuidValue() }
                .toTypedArray()
            params = uowEvent.params
        }
    }

    override suspend fun add(uowEvent: UowEvent) {
        try {
            insert(
                dslContext.insertQuery(UOW_EVENTS).apply {
                    setRecord(toUERecord(uowEvent))
                },
            )
        } catch (ex: Exception) {
            val constraintName = when {
                ex is DataAccessException && ex.sqlState() == PG_UNIQUE_VIOLATION ->
                    extractUniqueConstraintName(queryExecutor, UOW_EVENTS, ex)
                ex is PgException && ex.sqlState == PG_UNIQUE_VIOLATION -> ex.constraint
                else -> throw ex
            }
            throw UniqueUowEventRecordViolationException(
                uowEvent.id.uuidValue(),
                uowEvent.uowName.stringValue(),
                uowEvent.idempotencyKey,
                constraintName
            )
        }

        val modelEventRs = uowEvent.modelEvents.map { (id, event) ->
            toMERecord(
                uowEvent = uowEvent,
                eventId = id,
                modelEvent = event
            ).also {
                val tracingContext = contextMap(openTelemetry.propagators.textMapPropagator)
                if (tracingContext.isNotEmpty()) {
                    it.tracingContext = json.encodeToString(tracingContext)
                }
            }
        }
        if (modelEventRs.isNotEmpty()) {
            insert(
                dslContext.insertQuery(MODEL_EVENTS).apply {
                    for (mer in modelEventRs) {
                        addRecord(mer)
                    }
                },
            )
        }
    }

    private suspend fun <R : Record> insert(query: InsertQuery<R>) {
        queryExecutor.executeQuery(
            dslContext = dslContext,
            jooqQuery = query,
        )
    }

    private fun ModelEvent<*>.payload(principal: Principal<*>): JsonObject {
        return when (this) {
            is ModelWithPrincipalEvent -> {
                val principalPayload = buildJsonObject {
                    put("principalId", principal.id.toString())
                    put("principalName", principal.name.toString())
                }
                return JsonObject(principalPayload + integrationEvent())
            }
            else -> {
                integrationEvent()
            }
        }
    }

    private fun String.utf8SizeInBytes(): Int {
        var totalBytes = 0
        val charIterator = this.iterator()
        for (character in charIterator) {
            when {
                character <= '\u007F' -> totalBytes += 1
                character <= '\u07FF' -> totalBytes += 2
                Character.isHighSurrogate(character) -> {
                    totalBytes += 4
                    if (charIterator.hasNext()) {
                        charIterator.next()
                    }
                }
                else -> totalBytes += 3
            }
        }
        return totalBytes
    }
}
