package com.razz.eva.events

import com.razz.eva.IdempotencyKey
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.*

class InMemoryEventBusSpec : FunSpec({

    test("Not started event bus does not accept events") {
        val notStarted = InMemoryEventBus(listOf(consumer()))
        shouldThrow<IllegalStateException> { notStarted.publish(uowEvent()) }
    }

    test("Started and closed event bus does not accept events") {
        val startedAndClosed = InMemoryEventBus(listOf(consumer())).apply {
            start()
            close()
        }
        shouldThrow<IllegalStateException> { startedAndClosed.publish(uowEvent()) }
    }

    test("Event bus distributes event to consumer") {
        val uowEvent = uowEvent()
        val (modelEventId, modelEvent) = uowEvent.modelEvents.entries.first()
        val (chan, consumer) = consumer(modelEvent) { event ->
            matchConsumed(event, modelEvent, modelEventId, uowEvent)
        }
        val bus = InMemoryEventBus(listOf(consumer)).apply {
            start()
        }
        println(
            "____PUBLISHING____: " + uowEvent.id
        )
        bus.publish(uowEvent)
        println(
            "____VALIDATING____: " + uowEvent.id
        )
        validateResult(chan)
    }

    test("Event bus distributes event to all interested consumers") {
        val uowEvent = uowEvent()
        val (modelEventId, modelEvent) = uowEvent.modelEvents.entries.first()
        val (chan0, consumer0) = consumer(modelEvent) { event ->
            matchConsumed(event, modelEvent, modelEventId, uowEvent)
        }
        val (chan1, consumer1) = consumer(modelEvent) { event ->
            matchConsumed(event, modelEvent, modelEventId, uowEvent)
        }
        val bus = InMemoryEventBus(listOf(consumer0, consumer1)).apply {
            start()
        }
        println(
            "____PUBLISHING____: " + uowEvent.id
        )
        bus.publish(uowEvent)
        println(
            "____VALIDATING____: " + uowEvent.id
        )
        validateResult(chan0)
        validateResult(chan1)
    }

    test("Event bus distributes different events to dedicated consumers") {
        val uowEvent = uowEvent(TestModelEvent0, TestModelEvent1, TestModelEvent2)
        val (modelEventId0, modelEvent0) = uowEvent.modelEvents.entries.toList()[0]
        val (modelEventId1, modelEvent1) = uowEvent.modelEvents.entries.toList()[1]
        val (modelEventId2, modelEvent2) = uowEvent.modelEvents.entries.toList()[2]
        val (chan0, consumer0) = consumer(modelEvent0) { event ->
            matchConsumed(event, modelEvent0, modelEventId0, uowEvent)
        }
        val (chan1, consumer1) = consumer(modelEvent1) { event ->
            matchConsumed(event, modelEvent1, modelEventId1, uowEvent)
        }
        val (chan2, consumer2) = consumer(modelEvent2) { event ->
            matchConsumed(event, modelEvent2, modelEventId2, uowEvent)
        }
        val bus = InMemoryEventBus(listOf(consumer0, consumer1, consumer2)).apply {
            start()
        }
        println(
            "____PUBLISHING____: " + uowEvent.id
        )
        bus.publish(uowEvent)
        println(
            "____VALIDATING____: " + uowEvent.id
        )
        validateResult(chan0)
        validateResult(chan1)
        validateResult(chan2)
    }
})

private fun matchConsumed(
    event: IntegrationModelEvent,
    modelEvent: ModelEvent<*>,
    modelEventId: UowEvent.ModelEventId,
    uowEvent: UowEvent,
) {
    event.eventName.toString() shouldBe modelEvent.eventName()
    event.id.toUUID() shouldBe modelEventId.uuidValue()
    event.modelId.stringValue() shouldBe modelEvent.modelId.stringValue()
    event.occurredAt shouldBe uowEvent.occurredAt
    event.modelName.toString() shouldBe modelEvent.modelName
    event.uowId.toUUID() shouldBe uowEvent.id.uuidValue()
    event.payload shouldBe modelEvent.integrationEvent()
}

sealed interface Result {
    object Ok : Result
    data class Error(val e: Throwable) : Result
}

object TestPrincipal : Principal<UUID> {
    override val id = Principal.Id(UUID.randomUUID())
    override val name = Principal.Name("TestPrincipal")
}

object TestModelId0 : ModelId<String> {
    override val id = "TestModelId0"
}

object TestModelId1 : ModelId<String> {
    override val id = "TestModelId0"
}

object TestModelEvent0 : ModelEvent<TestModelId0> {
    override val modelId = TestModelId0
    override val modelName = "TestModel0"
}

object TestModelEvent1 : ModelEvent<TestModelId1> {
    override val modelId = TestModelId1
    override val modelName = "TestModel1"
}

object TestModelEvent2 : ModelEvent<TestModelId1> {
    override val modelId = TestModelId1
    override val modelName = "TestModel1"
}

private fun uowEvent(vararg modelEvents: ModelEvent<*> = arrayOf(TestModelEvent0)) = UowEvent(
    id = UowEvent.Id.random(),
    uowName = UowEvent.UowName("Fake"),
    principal = TestPrincipal,
    modelEvents = modelEvents.associateBy { UowEvent.ModelEventId.random() },
    idempotencyKey = IdempotencyKey.random(),
    params = buildJsonObject {
        put("id", "some-id")
    },
    occurredAt = Instant.now()
)

private suspend fun validateResult(chan: Channel<Result>) {
    when (val res = withTimeout(5.toDuration(DurationUnit.SECONDS)) {
        chan.receive()
    }) {
        is Result.Error -> throw res.e
        Result.Ok -> {}
    }
}

private fun consumer(
    modelEvent: ModelEvent<*>,
    matcher: suspend (IntegrationModelEvent) -> Unit
): Pair<Channel<Result>, EventConsumer> {
    val chan = Channel<Result>(capacity = 100) { }
    return chan to object : EventConsumer {
        override val modelName = IntegrationModelEvent.ModelName(modelEvent.modelName)
        override val eventNames = setOf(IntegrationModelEvent.EventName(modelEvent.eventName()))
        override suspend fun consume(event: IntegrationModelEvent) {
            try {
                println(
                    "____CONSUMING____: " + event.id
                )
                matcher(event)
                chan.send(Result.Ok)
                println(
                    "____CONSUMED____: " + event.id
                )
            } catch (e: Throwable) {
                chan.send(Result.Error(e))
                println(
                    "____FAILED TO CONSUME____: " + event.id
                )
            }
        }
    }
}

private fun consumer() = consumer(TestModelEvent0) { }.second
