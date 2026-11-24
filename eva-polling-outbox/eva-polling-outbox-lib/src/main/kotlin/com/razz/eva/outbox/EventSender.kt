package com.razz.eva.outbox

interface EventSender {
    fun send(events: List<OutboxEvent>): SendResult
}

data class SendResult(
    val acked: List<OutboxEvent>,
    val failure: Exception?,
)
