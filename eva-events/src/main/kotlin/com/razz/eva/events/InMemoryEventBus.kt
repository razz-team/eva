package com.razz.eva.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.Closeable
import java.util.concurrent.Executors.newSingleThreadExecutor

class InMemoryEventBus(
    consumers: List<EventConsumer>,
    extraBufferCapacity: Int = 100,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    private val context: ExecutorCoroutineDispatcher = newSingleThreadExecutor().asCoroutineDispatcher()
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
                    try {
                        consumerMap[EventKey(event.eventName, event.modelName)]?.forEach { consumer ->
                            consumer.consume(event)
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Unable to consume event [${event.eventName}]:[${event.id}]" }
                    }
                }
            }
        }
    }

    override suspend fun publish(uowEvent: UowEvent) {
        if (consumingJob?.isActive != true) {
            throw IllegalStateException("Event bus was closed or was not started and cant accept new events")
        }
        uowEvent.modelEvents.forEach { (id, event) ->
            val integrationEvent = IntegrationModelEvent(
                id = IntegrationModelEvent.EventId(id.uuidValue()),
                eventName = IntegrationModelEvent.EventName(event.eventName()),
                uowId = IntegrationModelEvent.UowId(uowEvent.id.uuidValue()),
                modelId = IntegrationModelEvent.ModelId(event.modelId.stringValue()),
                modelName = IntegrationModelEvent.ModelName(event.modelName),
                occurredAt = uowEvent.occurredAt,
                payload = event.integrationEvent()
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
}
