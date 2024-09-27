package com.razz.eva.repository

import com.razz.eva.domain.ModelEvent
import com.razz.eva.events.UowEvent
import com.razz.eva.events.UowEvent.ModelEventId
import com.razz.eva.events.db.tables.ModelEvents.MODEL_EVENTS
import com.razz.eva.events.db.tables.UowEvents.UOW_EVENTS
import com.razz.eva.events.db.tables.records.ModelEventsRecord
import com.razz.eva.events.db.tables.records.UowEventsRecord
import com.razz.eva.persistence.PersistenceException.UniqueUowEventRecordViolationException
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.repository.Fake.FakeModelEvent
import com.razz.eva.repository.PgHelpers.PG_UNIQUE_VIOLATION
import com.razz.eva.repository.PgHelpers.extractUniqueConstraintName
import io.vertx.pgclient.PgException
import org.jooq.DSLContext
import org.jooq.InsertQuery
import org.jooq.Record
import org.jooq.exception.DataAccessException

class JooqEventRepository(
    private val queryExecutor: QueryExecutor,
    private val dslContext: DSLContext,
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
            tag = this::class.simpleName + "::warmup"
        ).firstOrNull()
    }

    private fun toMERecord(
        uowEvent: UowEvent,
        eventId: ModelEventId,
        modelEvent: ModelEvent<*>
    ): ModelEventsRecord {
        return ModelEventsRecord().apply {
            id = eventId.uuidValue()
            uowId = uowEvent.id.uuidValue()
            modelId = modelEvent.modelId.stringValue()
            name = modelEvent.eventName()
            modelName = modelEvent.modelName
            occurredAt = uowEvent.occurredAt
            payload = modelEvent.integrationEvent().toString()
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
            )
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
            tag = this::class.simpleName + "::insert"
        )
    }
}
