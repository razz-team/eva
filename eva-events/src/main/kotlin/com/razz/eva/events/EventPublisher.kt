package com.razz.eva.events

interface EventPublisher {

    suspend fun publish(uowEvent: UowEvent)
}
