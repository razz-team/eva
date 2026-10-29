package com.razz.eva.outbox.kafka

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.razz.eva.outbox.outboxEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.errors.NotLeaderOrFollowerException
import org.apache.kafka.common.errors.RecordTooLargeException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import org.apache.kafka.common.errors.TimeoutException as KafkaTimeoutException

class KafkaPublishingSpec : BehaviorSpec({

    val t0 = Instant.parse("2026-10-20T12:00:00Z")
    val mapper = KafkaRecordMapper(TopicResolver.single("model-events"))

    Given("Per-model topics of a service") {
        val resolver = TopicResolver.perServiceModel("kebab")

        Then("Topic is the service name and the model name") {
            resolver.topic(outboxEvent(insertedAt = t0).copy(modelName = "AppClient")) shouldBe "kebab-AppClient"
        }
    }

    Given("An event with tracing context") {
        val event = outboxEvent(
            insertedAt = t0,
            modelId = "dept-1",
            tracingContext = """{"traceparent":"00-abc-def-01"}""",
        )

        When("It is mapped to a record") {
            val record = mapper.toRecord(event)

            Then("Model id is the key, the model_events row is the value, there are no headers") {
                record.topic() shouldBe "model-events"
                record.key() shouldBe "dept-1"
                record.headers().toArray().shouldBeEmpty()
            }
            Then("Value is readable by eva event consumers") {
                consumerMapper.readValue<RawEvent>(record.value()) shouldBe RawEvent(
                    id = event.id,
                    uowId = event.uowId,
                    modelId = "dept-1",
                    modelName = event.modelName,
                    eventName = event.name,
                    occurredAt = Instant.parse("2026-10-20T12:00:00Z").toEpochMilli(),
                    payload = event.payload,
                    tracingContext = """{"traceparent":"00-abc-def-01"}""",
                )
            }
        }
    }

    Given("An event with malformed tracing context") {
        val event = outboxEvent(insertedAt = t0, tracingContext = "not json")

        When("It is mapped to a record") {
            val record = mapper.toRecord(event)

            Then("Tracing context is skipped") {
                consumerMapper.readValue<RawEvent>(record.value()).tracingContext shouldBe null
            }
        }
    }

    Given("A producer failing the second record") {
        val events = (0L until 3L).map { outboxEvent(insertedAt = t0.plusSeconds(it)) }
        val producer = mockk<Producer<String, ByteArray>>()
        val tooLarge = RecordTooLargeException("too large")
        every { producer.send(any()) } returnsMany listOf(
            CompletableFuture.completedFuture(mockk<RecordMetadata>()),
            CompletableFuture.failedFuture(tooLarge),
        )
        val sender = SequentialKafkaSender(producer, mapper, Duration.ofSeconds(1))

        When("Events are sent") {
            val result = sender.send(events)

            Then("Only the first one is acked and sending stops at the failure") {
                result.acked shouldContainExactly events.take(1)
                result.failure shouldBe tooLarge
                verify(exactly = 2) { producer.send(any()) }
            }
        }
    }

    Given("A batch sender and a producer failing two records") {
        val events = (0L until 4L).map { outboxEvent(insertedAt = t0.plusSeconds(it)) }
        val producer = mockk<Producer<String, ByteArray>>()
        val tooLarge = RecordTooLargeException("too large")
        val notLeader = NotLeaderOrFollowerException("not leader")
        every { producer.send(any()) } returnsMany listOf(
            CompletableFuture.completedFuture(mockk<RecordMetadata>()),
            CompletableFuture.failedFuture(tooLarge),
            CompletableFuture.failedFuture(notLeader),
            CompletableFuture.completedFuture(mockk<RecordMetadata>()),
        )
        val sender = BatchKafkaSender(producer, mapper, Duration.ofSeconds(1), Clock.systemUTC())

        When("Events are sent") {
            val result = sender.send(events)

            Then("All records are sent, the successful ones are acked and the first failure is returned") {
                verify(exactly = 4) { producer.send(any()) }
                result.acked shouldContainExactly listOf(events[0], events[3])
                result.failure shouldBe tooLarge
            }
        }
    }

    Given("A batch sender and a producer rejecting a record synchronously") {
        val events = (0L until 4L).map { outboxEvent(insertedAt = t0.plusSeconds(it)) }
        val producer = mockk<Producer<String, ByteArray>>()
        val tooLarge = RecordTooLargeException("too large")
        val metadataTimeout = KafkaTimeoutException("metadata is not available")
        every { producer.send(any()) } returns CompletableFuture.completedFuture(mockk<RecordMetadata>()) andThen
            CompletableFuture.failedFuture(tooLarge) andThenThrows metadataTimeout andThen
            CompletableFuture.completedFuture(mockk<RecordMetadata>())
        val sender = BatchKafkaSender(producer, mapper, Duration.ofSeconds(1), Clock.systemUTC())

        When("Events are sent") {
            val result = sender.send(events)

            Then("The rejected record is a failure, the other records are still sent and awaited") {
                verify(exactly = 4) { producer.send(any()) }
                result.acked shouldContainExactly listOf(events[0], events[3])
            }
            Then("The first failure in batch order is returned") {
                result.failure shouldBe tooLarge
            }
        }
    }

    Given("A batch sender and a record that is never acked") {
        val events = (0L until 2L).map { outboxEvent(insertedAt = t0.plusSeconds(it)) }
        val producer = mockk<Producer<String, ByteArray>>()
        every { producer.send(any()) } returns CompletableFuture<RecordMetadata>() andThen
            CompletableFuture.completedFuture(mockk<RecordMetadata>())
        val sender = BatchKafkaSender(producer, mapper, Duration.ofMillis(100), Clock.systemUTC())

        When("Events are sent") {
            val result = sender.send(events)

            Then("Waiting stops at the timeout, records acked by then are still collected") {
                result.acked shouldContainExactly events.drop(1)
                result.failure.shouldBeInstanceOf<TimeoutException>()
            }
        }
    }
})

// mirrors how eva event consumers read the record value
private val consumerMapper = jacksonObjectMapper().apply {
    disable(FAIL_ON_UNKNOWN_PROPERTIES)
}

private data class RawEvent(
    @get:JsonProperty("id") val id: UUID,
    @get:JsonProperty("uow_id") val uowId: UUID,
    @get:JsonProperty("model_id") val modelId: String,
    @get:JsonProperty("model_name") val modelName: String,
    @get:JsonProperty("name") val eventName: String,
    @get:JsonProperty("occurred_at") val occurredAt: Long,
    @get:JsonProperty("payload") val payload: String,
    @get:JsonProperty("tracing_context") val tracingContext: String?,
)
