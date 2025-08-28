package com.razz.eva.eventbus

import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelWithPrincipalEvent
import com.razz.eva.domain.Principal
import com.razz.eva.events.EventConsumer
import com.razz.eva.events.EventPublisher
import com.razz.eva.events.IntegrationModelEvent
import com.razz.eva.events.UowEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mu.KotlinLogging
import java.io.Closeable
import java.util.concurrent.Executors.newSingleThreadExecutor

class InMemoryEventBus(
    consumers: List<EventConsumer>,
    extraBufferCapacity: Int = 100,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    private val context: CoroutineDispatcher = newSingleThreadExecutor().asCoroutineDispatcher()
) : EventPublisher, Closeable {

    private val logger = KotlinLogging.logger {}
    private var consumingJob: Job? = null
    private val flow = MutableSharedFlow<IntegrationModelEvent>(0, extraBufferCapacity, onBufferOverflow)
    private val consumerMap: Map<EventKey, List<EventConsumer>> = consumers
        .flatMap { consumer -> consumer.eventNames.map { name -> EventKey(name, consumer.modelName) to consumer } }
        .groupByTo(mutableMapOf(), { (name, _) -> name }, { (_, consumer) -> consumer })

    fun start() {
        consumingJob = CoroutineScope(context).launch {
            flow.collect { event ->
                if (isActive) {
                    consumerMap[EventKey(event.eventName, event.modelName)]?.forEach { consumer ->
                        try {
                            consumer.consume(event)
                        } catch (ex: Exception) {
                            logger.error(ex) {
                                "Unable to consume event [${event.eventName}]:[${event.id}] " +
                                    "by consumer [${consumer::class.simpleName}]"
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun publish(uowEvent: UowEvent) {
        if (consumingJob?.isActive != true) {
            throw IllegalStateException("Event bus was closed or was not started and can't accept new events")
        }
        uowEvent.modelEvents.forEach { (id, event) ->
            val integrationEvent = IntegrationModelEvent(
                id = IntegrationModelEvent.EventId(id.uuidValue()),
                eventName = IntegrationModelEvent.EventName(event.eventName()),
                uowId = IntegrationModelEvent.UowId(uowEvent.id.uuidValue()),
                modelId = IntegrationModelEvent.ModelId(event.modelId.stringValue()),
                modelName = IntegrationModelEvent.ModelName(event.modelName),
                occurredAt = uowEvent.occurredAt,
                payload = event.payload(uowEvent.principal),
            )
            flow.emit(integrationEvent)
        }
    }

    private data class EventKey(
        val eventName: IntegrationModelEvent.EventName,
        val modelName: IntegrationModelEvent.ModelName
    )

    override fun close() {
        consumingJob?.cancel()
    }

    private fun ModelEvent<*>.payload(principal: Principal<*>): JsonObject {
        return when (this) {
            is ModelWithPrincipalEvent -> {
                val principalPayload = buildJsonObject {
                    put("principalId", principal.id.toString())
                    put("principalName", principal.name.toString())
                    put("principalContext", JsonObject(principal.context().mapValues { JsonPrimitive(it.value) }))
                }
                return JsonObject(principalPayload + integrationEvent())
            }
            else -> {
                integrationEvent()
            }
        }
    }
}
