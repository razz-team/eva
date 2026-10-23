package com.razz.eva.outbox

import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

fun outboxEvent(
    insertedAt: Instant,
    modelId: String = "model-1",
    id: UUID = randomUUID(),
    tracingContext: String = "{}",
) = OutboxEvent(
    id = id,
    uowId = randomUUID(),
    modelId = modelId,
    name = "SomethingHappened",
    modelName = "Model",
    occurredAt = insertedAt,
    insertedAt = insertedAt,
    payload = """{"id":"$id"}""",
    tracingContext = tracingContext,
)

class FakeOutboxStore(
    private val beforeFetch: FakeOutboxStore.(fetchNumber: Int) -> Unit = {},
) : OutboxStore {
    private val events = mutableListOf<OutboxEvent>()
    private val published = mutableSetOf<UUID>()
    val fetches = mutableListOf<Instant>()

    fun add(vararg event: OutboxEvent) {
        events += event
    }

    fun published(): Set<UUID> = published

    override fun fetchUnpublished(from: Instant, limit: Int): List<OutboxEvent> {
        beforeFetch(fetches.size)
        fetches += from
        return events
            .filter { it.id !in published && it.insertedAt >= from }
            .sortedBy { it.insertedAt }
            .take(limit)
    }

    override fun markPublished(events: List<OutboxEvent>): Int =
        events.count { published.add(it.id) }
}

class FakeEventSender(private val failOn: Set<UUID> = emptySet()) : EventSender {
    val sent = mutableListOf<OutboxEvent>()

    override fun send(events: List<OutboxEvent>): SendResult {
        val acked = events.takeWhile { it.id !in failOn }
        sent += acked
        val failure = if (acked.size < events.size) IllegalStateException("broker is down") else null
        return SendResult(acked, failure)
    }
}
