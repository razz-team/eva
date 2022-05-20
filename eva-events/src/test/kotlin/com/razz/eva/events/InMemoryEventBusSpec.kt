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

    fun consumer(matcher: suspend (IntegrationModelEvent) -> Unit): Pair<Channel<Result>, EventConsumer> {
        val chan = Channel<Result> { }
        return chan to object : EventConsumer {
            override val modelName = IntegrationModelEvent.ModelName("TestModel")
            override val eventNames = setOf(IntegrationModelEvent.EventName("TestModelEvent"))
            override suspend fun consume(event: IntegrationModelEvent) {
                try {
                    matcher(event)
                    chan.send(Result.Ok)
                } catch (e: Throwable) {
                    chan.send(Result.Error(e))
                }
            }
        }
    }
    fun consumer() = consumer { }.second
    val uowEvent = UowEvent(
        id = UowEvent.Id.random(),
        uowName = UowEvent.UowName("Fake"),
        principal = TestPrincipal,
        modelEvents = mapOf(UowEvent.ModelEventId.random() to TestModelEvent),
        idempotencyKey = IdempotencyKey.random(),
        params = buildJsonObject {
            put("id", "some-id")
        },
        occurredAt = Instant.now()
    )

    test("Not started event bus does not accept events") {
        val notStarted = InMemoryEventBus(listOf(consumer()))
        shouldThrow<IllegalStateException> { notStarted.publish(uowEvent) }
    }

    test("Started and closed event bus does not accept events") {
        val startedAndClosed = InMemoryEventBus(listOf(consumer())).apply {
            start()
            close()
        }
        shouldThrow<IllegalStateException> { startedAndClosed.publish(uowEvent) }
    }

    test("Event bus distributes event to consumer") {
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
        bus.publish(uowEvent)
        validateResult(chan)
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
private suspend fun validateResult(chan: Channel<Result>) {
    when (val res = withTimeout(5.toDuration(DurationUnit.SECONDS)) {
        chan.receive()
    }) {
        is Result.Error -> throw res.e
        Result.Ok -> {}
    }
}
