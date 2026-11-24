package com.razz.eva.outbox.kafka

import com.razz.eva.outbox.EventSender
import com.razz.eva.outbox.OutboxEvent
import com.razz.eva.outbox.SendResult
import mu.KotlinLogging
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration
import java.time.Instant
import java.time.InstantSource
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.MILLISECONDS

class BatchKafkaSender(
    private val producer: Producer<String, ByteArray>,
    private val mapper: KafkaRecordMapper,
    private val ackTimeout: Duration,
    private val clock: InstantSource,
) : EventSender {
    private val logger = KotlinLogging.logger {}

    override fun send(events: List<OutboxEvent>): SendResult {
        val deadline = clock.instant() + ackTimeout

        val futures = events.map { event ->
            val record = mapper.toRecord(event)
            // it is highly unlikely that one fails on send and the next one suceeds
            val future = producer.sendCatching(record)
            future to event
        }

        var failure: Exception? = null
        val acks = buildList {
            for ((future, event) in futures) {
                val e = future.await(deadline)
                when {
                    e == null -> add(event)
                    failure == null -> failure = e
                    else -> logger.warn(e) { "Failed to publish event ${event.id}, an earlier failure is reported" }
                }
            }
        }

        return SendResult(acks, failure)
    }

    private fun Producer<String, ByteArray>.sendCatching(record: ProducerRecord<String, ByteArray>) = try {
        send(record)
    } catch (ex: Exception) {
        CompletableFuture.failedFuture(ex)
    }

    private fun Future<*>.await(deadline: Instant): Exception? = try {
        val duration = Duration.between(clock.instant(), deadline)
        get(duration.toMillis(), MILLISECONDS)
        null
    } catch (ex: ExecutionException) {
        // unwrap it so the outbox failure carries the actual Kafka error, see SequentialKafkaSender
        ex.cause as? Exception ?: ex
    } catch (ex: Exception) {
        ex
    }
}
