package com.razz.eva.outbox

import java.time.Instant
import java.util.UUID

data class OutboxEvent(
    val id: UUID,
    val uowId: UUID,
    val modelId: String,
    val name: String,
    val modelName: String,
    val occurredAt: Instant,
    val insertedAt: Instant,
    val payload: String,
    val tracingContext: String,
)
