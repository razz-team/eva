package com.razz.eva.outbox.kafka

import com.razz.eva.outbox.EventSender
import com.razz.eva.outbox.OutboxEvent
import com.razz.eva.outbox.SendResult
import org.apache.kafka.clients.producer.Producer
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit.MILLISECONDS

class SequentialKafkaSender(
    private val producer: Producer<String, ByteArray>,
    private val mapper: KafkaRecordMapper,
    private val ackTimeout: Duration,
) : EventSender {

    override fun send(events: List<OutboxEvent>): SendResult {
        val acked = ArrayList<OutboxEvent>(events.size)
        for (event in events) {
            val failure = sendAndAwait(event)
            if (failure != null) return SendResult(acked, failure)
            acked += event
        }
        return SendResult(acked, null)
    }

    private fun sendAndAwait(event: OutboxEvent): Exception? = try {
        producer.send(mapper.toRecord(event)).get(ackTimeout.toMillis(), MILLISECONDS)
        null
    } catch (ex: ExecutionException) {
        // Future.get wraps every broker-side failure (RecordTooLargeException, TimeoutException, auth errors, ...)
        // in ExecutionException; unwrap it so the outbox failure carries the actual Kafka error
        ex.cause as? Exception ?: ex
    } catch (ex: Exception) {
        ex
    }
}
