package com.razz.eva.events

import com.razz.eva.IdempotencyKey
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.engine.test.logging.warn
import io.kotest.matchers.shouldBe
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.*

@OptIn(ExperimentalKotest::class)
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
        val (chan, consumer) = consumer { event ->
            event.eventName.toString() shouldBe modelEvent.eventName()
            event.id.toUUID() shouldBe modelEventId.uuidValue()
            event.modelId.stringValue() shouldBe modelEvent.modelId.stringValue()
            event.occurredAt shouldBe uowEvent.occurredAt
            event.modelName.toString() shouldBe modelEvent.modelName
            event.uowId.toUUID() shouldBe uowEvent.id.uuidValue()
            event.payload shouldBe modelEvent.integrationEvent()
        }
        val bus = InMemoryEventBus(listOf(consumer)).apply {
            start()
        }
        this@test.warn {
            "____PUBLISHING____: " + uowEvent.id
        }
        bus.publish(uowEvent)
        this@test.warn {
            "____VALIDATING____: " + uowEvent.id
        }
        validateResult(chan)
    }

    test("Event bus distributes event to all interested consumers") {
        val uowEvent = uowEvent()
        val (modelEventId, modelEvent) = uowEvent.modelEvents.entries.first()
        val (chan0, consumer0) = consumer { event ->
            event.eventName.toString() shouldBe modelEvent.eventName()
            event.id.toUUID() shouldBe modelEventId.uuidValue()
            event.modelId.stringValue() shouldBe modelEvent.modelId.stringValue()
            event.occurredAt shouldBe uowEvent.occurredAt
            event.modelName.toString() shouldBe modelEvent.modelName
            event.uowId.toUUID() shouldBe uowEvent.id.uuidValue()
            event.payload shouldBe modelEvent.integrationEvent()
        }
        val (chan1, consumer1) = consumer { event ->
            event.eventName.toString() shouldBe modelEvent.eventName()
            event.id.toUUID() shouldBe modelEventId.uuidValue()
            event.modelId.stringValue() shouldBe modelEvent.modelId.stringValue()
            event.occurredAt shouldBe uowEvent.occurredAt
            event.modelName.toString() shouldBe modelEvent.modelName
            event.uowId.toUUID() shouldBe uowEvent.id.uuidValue()
            event.payload shouldBe modelEvent.integrationEvent()
        }
        val bus = InMemoryEventBus(listOf(consumer0, consumer1)).apply {
            start()
        }
        this@test.warn {
            "____PUBLISHING____: " + uowEvent.id
        }
        bus.publish(uowEvent)
        this@test.warn {
            "____VALIDATING____: " + uowEvent.id
        }
        validateResult(chan0)
        validateResult(chan1)
    }
})

sealed interface Result {
    object Ok : Result
    data class Error(val e: Throwable) : Result
}

object TestPrincipal : Principal<UUID> {
    override val id = Principal.Id(UUID.randomUUID())
    override val name = Principal.Name("TestPrincipal")
}

object TestModelId : ModelId<String> {
    override val id = "TestModelId"
}

object TestModelEvent : ModelEvent<TestModelId> {
    override val modelId = TestModelId
    override val modelName = "TestModel"
}

private fun uowEvent(vararg modelEvents: TestModelEvent = arrayOf(TestModelEvent)) = UowEvent(
    id = UowEvent.Id.random(),
    uowName = UowEvent.UowName("Fake"),
    principal = TestPrincipal,
    modelEvents = modelEvents.associate { UowEvent.ModelEventId.random() to TestModelEvent },
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

@OptIn(ExperimentalKotest::class)
private fun TestScope.consumer(matcher: suspend (IntegrationModelEvent) -> Unit): Pair<Channel<Result>, EventConsumer> {
    val chan = Channel<Result>(capacity = 100) { }
    return chan to object : EventConsumer {
        override val modelName = IntegrationModelEvent.ModelName("TestModel")
        override val eventNames = setOf(IntegrationModelEvent.EventName("TestModelEvent"))
        override suspend fun consume(event: IntegrationModelEvent) {
            try {
                this@consumer.warn {
                    "____CONSUMING____: " + event.id
                }
                matcher(event)
                chan.send(Result.Ok)
                this@consumer.warn {
                    "____CONSUMED____: " + event.id
                }
            } catch (e: Throwable) {
                chan.send(Result.Error(e))
                this@consumer.warn {
                    "____FAILED TO CONSUME____: " + event.id
                }
            }
        }
    }
}

private fun TestScope.consumer() = consumer { }.second
