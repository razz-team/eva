package com.razz.eva.repository

import com.razz.eva.IdempotencyKey
import com.razz.eva.events.IntegrationModelEvent
import com.razz.eva.events.IntegrationModelEvent.EventId
import com.razz.eva.events.IntegrationModelEvent.EventName
import com.razz.eva.events.IntegrationModelEvent.ModelId
import com.razz.eva.events.IntegrationModelEvent.ModelName
import com.razz.eva.events.IntegrationModelEvent.UowId
import com.razz.eva.events.UowEvent
import com.razz.eva.events.db.tables.ModelEvents.MODEL_EVENTS
import com.razz.eva.events.db.tables.UowEvents.UOW_EVENTS
import com.razz.eva.events.db.tables.records.ModelEventsRecord
import com.razz.eva.events.db.tables.records.UowEventsRecord
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.serialization.json.JsonFormat.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jooq.Condition
import org.jooq.DSLContext
import java.time.Instant

class EventQueries(
    private val queryExecutor: QueryExecutor,
    private val dslContext: DSLContext
) {

    data class PersistedUowEvent(
        val id: UowEvent.Id,
        val uowName: UowEvent.UowName,
        val principalId: String,
        val principalName: String,
        val modelEvents: List<Pair<IntegrationModelEvent, JsonObject>>,
        val occurredAt: Instant,
        val params: JsonObject
    )

    private fun fromMERecord(record: ModelEventsRecord) =
        IntegrationModelEvent(
            id = EventId(record.id),
            eventName = EventName(record.name),
            uowId = UowId(record.uowId),
            modelId = ModelId(record.modelId),
            modelName = ModelName(record.modelName),
            occurredAt = record.occurredAt,
            payload = json.parseToJsonElement(record.payload).jsonObject
        ) to Json.parseToJsonElement(record.tracingContext).jsonObject

    suspend fun getUowEvent(idempotencyKey: IdempotencyKey): PersistedUowEvent {
        val uowEvent = findSingleUowEvent(
            UOW_EVENTS.IDEMPOTENCY_KEY.eq(idempotencyKey.stringValue())
        )
        val modelEvents = findModelEvents(MODEL_EVENTS.UOW_ID.eq(uowEvent.id))

        return PersistedUowEvent(
            id = UowEvent.Id(uowEvent.id),
            uowName = UowEvent.UowName(uowEvent.name),
            principalId = uowEvent.principalId,
            principalName = uowEvent.principalName,
            occurredAt = uowEvent.occurredAt,
            modelEvents = modelEvents.map(::fromMERecord)
                .sortedBy {
                    uowEvent.modelEvents.indexOf(it.first.id.toUUID())
                },
            params = Json.parseToJsonElement(uowEvent.params).jsonObject
        )
    }

    private suspend fun findSingleUowEvent(condition: Condition): UowEventsRecord {
        val selectQuery = dslContext.selectQuery(UOW_EVENTS)
        selectQuery.addConditions(condition)
        return queryExecutor.executeSelect(
            dslContext = dslContext,
            jooqQuery = selectQuery,
            table = UOW_EVENTS
        ).single()
    }

    private suspend fun findModelEvents(condition: Condition): List<ModelEventsRecord> {
        val selectQuery = dslContext.selectQuery(MODEL_EVENTS)
        selectQuery.addConditions(condition)
        return queryExecutor.executeSelect(
            dslContext = dslContext,
            jooqQuery = selectQuery,
            table = MODEL_EVENTS
        )
    }
}
