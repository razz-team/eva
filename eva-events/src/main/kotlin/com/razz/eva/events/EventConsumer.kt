package com.razz.eva.events

import com.razz.eva.events.IntegrationModelEvent.EventName
import com.razz.eva.events.IntegrationModelEvent.ModelName

interface EventConsumer {

    @JvmInline
    value class ConsumerId(private val id: String) {
        override fun toString() = id
    }

    val modelName: ModelName

    val eventNames: Set<EventName>

    suspend fun consume(event: IntegrationModelEvent)
}
