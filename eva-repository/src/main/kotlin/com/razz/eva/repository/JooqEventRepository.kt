package com.razz.eva.repository

import com.razz.eva.domain.ModelEvent
import com.razz.eva.events.db.tables.ModelEvents.MODEL_EVENTS
import com.razz.eva.events.db.tables.UowEvents.UOW_EVENTS
import com.razz.eva.events.db.tables.records.ModelEventsRecord
import com.razz.eva.events.db.tables.records.UowEventsRecord
import com.razz.eva.persistence.PersistenceException.UniqueUowEventRecordViolationException
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.repository.Fake.FakeModelEvent
import com.razz.eva.repository.PgHelpers.PG_UNIQUE_VIOLATION
import com.razz.eva.repository.PgHelpers.extractUniqueConstraintName
import com.razz.eva.uow.UowEvent
import com.razz.eva.uow.UowEvent.ModelEventId
import com.razz.eva.uow.params.UowParams
import com.razz.eva.serialization.json.JsonFormat.json
import com.razz.eva.tracing.ActiveSpanElement
import io.opentracing.Tracer
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapAdapter
import io.vertx.pgclient.PgException
import kotlinx.serialization.encodeToString
import org.jooq.DSLContext
import org.jooq.InsertQuery
import org.jooq.Record
import org.jooq.Table
import org.jooq.exception.DataAccessException
import kotlin.coroutines.coroutineContext

class JooqEventRepository(
    private val queryExecutor: QueryExecutor,
    private val dslContext: DSLContext,
    private val tracer: Tracer
) : EventRepository {

    override suspend fun warmup() {
        toUERecord(Fake.uowEvent)
        toMERecord(
            uowEvent = Fake.uowEvent,
            eventId = FakeModelEvent.eventId,
            modelEvent = FakeModelEvent
        )
        val select = dslContext.selectFrom(MODEL_EVENTS)
            .orderBy(MODEL_EVENTS.OCCURRED_AT.desc())
            .limit(1)
        queryExecutor.executeSelect(
            dslContext = dslContext,
            jooqQuery = select,
            fields = select.fields().asList(),
            recordType = select.recordType
        ).firstOrNull()
    }

    private fun toMERecord(
        uowEvent: UowEvent<*>,
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

    private fun <P : UowParams<P>> toUERecord(uowEvent: UowEvent<P>): UowEventsRecord {
        return UowEventsRecord().apply {
            id = uowEvent.id.uuidValue()
            name = uowEvent.uowName.toString()
            idempotencyKey = uowEvent.params.idempotencyKey?.stringValue()
            principalId = uowEvent.principal.id.toString()
            principalName = uowEvent.principal.name.toString()
            occurredAt = uowEvent.occurredAt
            modelEvents = uowEvent.modelEvents
                .map { (id, _) -> id.uuidValue() }
                .toTypedArray()
            params = json.encodeToString(uowEvent.params.serialization(), uowEvent.params)
        }
    }

    override suspend fun <P : UowParams<P>> add(uowEvent: UowEvent<P>) {
        try {
            insert(
                dslContext.insertQuery(UOW_EVENTS).apply {
                    setRecord(toUERecord(uowEvent))
                },
                UOW_EVENTS
            )
        } catch (e: Exception) {
            val constraintName = when {
                e is DataAccessException && e.sqlState() == PG_UNIQUE_VIOLATION ->
                    extractUniqueConstraintName(queryExecutor, UOW_EVENTS, e)
                e is PgException && e.code == PG_UNIQUE_VIOLATION -> e.constraint
                else -> throw e
            }
            throw UniqueUowEventRecordViolationException(
                uowEvent.id,
                uowEvent.uowName,
                uowEvent.params.idempotencyKey,
                constraintName
            )
        }

        val tracingContext = coroutineContext[ActiveSpanElement]?.span?.context()?.let {
            mutableMapOf<String, String>().apply {
                tracer.inject(it, Format.Builtin.TEXT_MAP, TextMapAdapter(this))
            }
        }
        val modelEventRs = uowEvent.modelEvents.map { (id, event) ->
            toMERecord(
                uowEvent = uowEvent,
                eventId = id,
                modelEvent = event
            ).also { record ->
                if (tracingContext != null) {
                    record.tracingContext = json.encodeToString(tracingContext)
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
                MODEL_EVENTS
            )
        }
    }

    private suspend fun <R : Record> insert(query: InsertQuery<R>, table: Table<R>) {
        queryExecutor.executeStore(
            dslContext = dslContext,
            jooqQuery = query,
            fields = table.fields().asList(),
            recordType = table.recordType
        )
    }
}
