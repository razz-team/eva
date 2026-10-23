package com.razz.eva.outbox.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import org.apache.kafka.common.KafkaException
import java.time.Duration

class OutboxProducerSpec : BehaviorSpec({

    val bootstrapServers = "localhost:9092"

    Given("Default timeouts") {
        Then("Delivery timeout of request timeout plus linger is accepted") {
            OutboxProducer.create(bootstrapServers).close()
        }
    }

    Given("A custom request timeout") {
        Then("Delivery timeout follows it") {
            OutboxProducer.create(bootstrapServers, requestTimeout = Duration.ofSeconds(45)).close()
        }
    }

    Given("Delivery timeout shorter than request timeout plus linger") {
        Then("Producer is rejected") {
            val ex = shouldThrow<KafkaException> {
                OutboxProducer.create(
                    bootstrapServers,
                    linger = Duration.ofMillis(10),
                    requestTimeout = Duration.ofSeconds(10),
                    deliveryTimeout = Duration.ofMillis(10_009),
                )
            }
            ex.cause?.message.orEmpty() shouldContain "delivery.timeout.ms"
        }
    }
})
