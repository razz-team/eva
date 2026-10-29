package com.razz.eva.outbox.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration

object OutboxProducer {

    /**
     * Creates a producer for the outbox. `acks` is always `all`: an event is marked as published once acked,
     * so the ack must survive a leader failover.
     *
     * @param bootstrapServers comma-separated `host:port` list of brokers (`bootstrap.servers`)
     * @param maxInFlightRequestsPerConnection max unacknowledged requests per broker connection
     *   (`max.in.flight.requests.per.connection`)
     * @param maxRequestSize max size of a request, and so of a single record, before compression
     *   (`max.request.size`); the topic's `max.message.bytes` has to allow it as well
     * @param linger how long a record waits for others to join its batch before being sent (`linger.ms`)
     * @param requestTimeout how long to wait for a broker response before retrying the request
     *   (`request.timeout.ms`)
     * @param deliveryTimeout upper bound for reporting a send as succeeded or failed, retries included
     *   (`delivery.timeout.ms`); must be at least [linger] plus [requestTimeout]
     * @param maxBlock how long `send` may block waiting for topic metadata or buffer space (`max.block.ms`)
     * @param clientId identifies the producer in broker logs, metrics and quotas (`client.id`)
     * @param compression compression of record batches (`compression.type`)
     * @param security connection security, see [KafkaSecurity]
     */
    fun create(
        bootstrapServers: String,
        maxInFlightRequestsPerConnection: Int = 5,
        maxRequestSize: Int = 2 * 1024 * 1024,
        linger: Duration = Duration.ofMillis(10),
        requestTimeout: Duration = Duration.ofSeconds(10),
        deliveryTimeout: Duration = requestTimeout + linger,
        maxBlock: Duration = Duration.ofSeconds(10),
        clientId: String = "eva-polling-outbox",
        compression: Compression = Compression.LZ4,
        security: KafkaSecurity = KafkaSecurity.Plaintext,
    ): Producer<String, ByteArray> {
        return create(
            buildMap {
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
                put(ProducerConfig.ACKS_CONFIG, "all")
                put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, maxInFlightRequestsPerConnection)
                put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, maxRequestSize)
                put(ProducerConfig.LINGER_MS_CONFIG, linger.toMillis())
                put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeout.toMillis().toInt())
                put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeout.toMillis().toInt())
                put(ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlock.toMillis())
                put(ProducerConfig.CLIENT_ID_CONFIG, clientId)
                put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compression.configValue)
                putAll(security.properties())
            },
        )
    }

    fun create(properties: Map<String, Any>): Producer<String, ByteArray> {
        return KafkaProducer(properties, StringSerializer(), ByteArraySerializer())
    }

    enum class Compression(val configValue: String) {
        NONE("none"),
        GZIP("gzip"),
        SNAPPY("snappy"),
        LZ4("lz4"),
        ZSTD("zstd"),
    }
}
