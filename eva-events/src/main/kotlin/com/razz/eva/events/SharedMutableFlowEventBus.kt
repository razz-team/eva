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
import java.util.concurrent.Executors

class SharedMutableFlowEventBus(
    replay: Int,
    extraBufferCapacity: Int,
    onBufferOverflow: BufferOverflow,
    consumers: List<EventConsumer>,
    val context: ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
) : EventPublisher, Closeable {

    private val logger = KotlinLogging.logger {}
    private val consumingJob = Job()
    private val delegate = MutableSharedFlow<IntegrationModelEvent>(replay, extraBufferCapacity, onBufferOverflow)
    private val consumerMap: Map<IntegrationModelEvent.EventName, List<EventConsumer>> = consumers
        .flatMap { consumer -> consumer.eventNames.map { name -> name to consumer } }
        .groupByTo(mutableMapOf(), { (name, _) -> name }, { (_, consumer) -> consumer })

    fun start() {
        CoroutineScope(context + consumingJob).launch {
            delegate.collect { event ->
                if (isActive) {
                    try {
                        consumerMap[event.eventName]?.forEach { consumer ->
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
            delegate.tryEmit(integrationEvent)
        }
    }

    override fun close() {
        consumingJob.cancel()
    }
}
