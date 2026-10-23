package com.razz.eva.outbox.db

import com.razz.eva.outbox.OutboxEvent
import com.razz.eva.outbox.OutboxStore
import com.razz.eva.outbox.db.schema.tables.references.MODEL_EVENTS
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.time.Instant
import java.time.InstantSource
import javax.sql.DataSource

class JooqOutboxStore(
    private val dsl: DSLContext,
    private val instantSource: InstantSource,
) : OutboxStore {

    constructor(dataSource: DataSource, instantSource: InstantSource) :
        this(DSL.using(dataSource, SQLDialect.POSTGRES), instantSource)

    override fun fetchUnpublished(from: Instant, limit: Int): List<OutboxEvent> {
        return dsl
            .selectFrom(MODEL_EVENTS)
            .where(MODEL_EVENTS.PUBLISHED_AT.isNull)
            .and(MODEL_EVENTS.INSERTED_AT.ge(from))
            .orderBy(MODEL_EVENTS.INSERTED_AT, MODEL_EVENTS.INCREMENTAL_QUERY_ID)
            .limit(limit)
            .fetch { r ->
                OutboxEvent(
                    id = r.id,
                    uowId = r.uowId,
                    modelId = r.modelId,
                    name = r.name,
                    modelName = r.modelName,
                    occurredAt = r.occurredAt,
                    // NOT NULL columns with a default are nullable in the generated record
                    insertedAt = checkNotNull(r.insertedAt),
                    payload = r.payload,
                    tracingContext = checkNotNull(r.tracingContext),
                )
            }
    }

    override fun markPublished(events: List<OutboxEvent>): Int {
        if (events.isEmpty()) return 0
        // model_events has no index on id: the inserted_at range prunes partitions and uses the partial index
        return dsl
            .update(MODEL_EVENTS)
            .set(MODEL_EVENTS.PUBLISHED_AT, instantSource.instant())
            .where(MODEL_EVENTS.PUBLISHED_AT.isNull)
            .and(MODEL_EVENTS.INSERTED_AT.between(events.minOf { it.insertedAt }, events.maxOf { it.insertedAt }))
            .and(MODEL_EVENTS.ID.`in`(events.map { it.id }))
            .execute()
    }
}
