package com.razz.eva.outbox

import java.time.Instant

interface OutboxStore {
    fun fetchUnpublished(from: Instant, limit: Int): List<OutboxEvent>
    fun markPublished(events: List<OutboxEvent>): Int
}
