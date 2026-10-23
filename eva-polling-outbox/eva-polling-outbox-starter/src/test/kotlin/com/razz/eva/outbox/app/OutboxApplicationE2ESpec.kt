package com.razz.eva.outbox.app

import com.razz.eva.migrations.Migration.Factory.EventsMigration
import com.razz.eva.migrations.Migrations
import com.razz.eva.test.db.DatabaseContainer
import com.razz.eva.test.db.DatabaseContainerHelper
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import org.testcontainers.kafka.ConfluentKafkaContainer
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.MILLIS
import java.util.UUID
import java.util.UUID.randomUUID
import kotlin.time.Duration.Companion.seconds

class OutboxApplicationE2ESpec : BehaviorSpec({

    val db = DatabaseContainerHelper.create("eva_outbox_app_test")
    val dataSource = DatabaseContainer.BASIC.localPool(db.dbName(), 4)
    val dsl = DSL.using(dataSource, POSTGRES)
    Migrations(dataSource, EventsMigration, *arrayOf()).start()

    val kafka = ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0").apply { start() }
    afterSpec { kafka.stop() }

    // returns the record value the outbox is expected to publish for the inserted row
    fun insertEvent(insertedAt: LocalDateTime): JsonObject {
        val id = randomUUID()
        val uowId = randomUUID()
        // record value keeps occurred_at in epoch millis
        val occurredAt = insertedAt.minusMinutes(1).truncatedTo(MILLIS)
        val payload = """{"id":"$id"}"""
        val tracingContext = """{"traceparent":"00-$id-01"}"""
        dsl.execute(
            """
            INSERT INTO events.model_events
                (id, uow_id, model_id, name, model_name, occurred_at, inserted_at, payload, tracing_context)
            VALUES (?, ?, 'model-1', 'SomethingHappened', 'AppClient', ?, ?, ?, ?)
            """.trimIndent(),
            id, uowId, occurredAt, insertedAt, payload, tracingContext,
        )
        return buildJsonObject {
            put("id", id.toString())
            put("uow_id", uowId.toString())
            put("model_id", "model-1")
            put("model_name", "AppClient")
            put("name", "SomethingHappened")
            put("occurred_at", occurredAt.toInstant(UTC).toEpochMilli())
            put("payload", payload)
            put("tracing_context", tracingContext)
        }
    }

    fun unpublished(): List<UUID> = dsl
        .fetch("SELECT id FROM events.model_events WHERE published_at IS NULL")
        .map { it.get(0, UUID::class.java) }

    fun consume(topic: String, count: Int): List<ConsumerRecord<String, ByteArray>> {
        val consumer = KafkaConsumer(
            mapOf<String, Any>(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ),
            StringDeserializer(),
            ByteArrayDeserializer(),
        )
        return consumer.use {
            it.subscribe(listOf(topic))
            val records = mutableListOf<ConsumerRecord<String, ByteArray>>()
            val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
            while (records.size < count && System.nanoTime() < deadline) {
                records += it.poll(Duration.ofMillis(200))
            }
            records
        }
    }

    fun ConsumerRecord<String, ByteArray>.valueJson() = Json.parseToJsonElement(String(value())).jsonObject

    Given("Outbox application started from a config") {
        val config = ConfigLoader.parse(
            """
            scheduler: &database
              jdbcUrl: ${'$'}{DB_URL}
              username: ${'$'}{DB_USERNAME}
              password: ${'$'}{DB_PASSWORD}
            tasks:
              app:
                serviceName: kebab
                database: *database
            kafka:
              bootstrapServers: ${kafka.bootstrapServers}
            """.trimIndent(),
            env = mapOf("DB_URL" to db.jdbcUrl(), "DB_USERNAME" to db.username(), "DB_PASSWORD" to db.password()),
        )
        val application = OutboxApplication(config)
        application.start()
        afterContainer { application.close() }

        When("Events are inserted") {
            val now = LocalDateTime.now()
            val expected = (0L until 3L).map { insertEvent(now.plusSeconds(it)) }

            Then("They are published to the service's model topic") {
                eventually(30.seconds) {
                    unpublished().shouldBeEmpty()
                }
                consume("kebab-AppClient", expected.size)
                    .map { UUID.fromString(it.valueJson().getValue("id").jsonPrimitive.content) } shouldContainExactly
                    expected.map { UUID.fromString(it.getValue("id").jsonPrimitive.content) }
            }
            Then("Records are keyed by model id and carry the model_events rows") {
                val records = consume("kebab-AppClient", expected.size)
                records.map { it.key() } shouldContainExactly List(expected.size) { "model-1" }
                records.map { it.valueJson() } shouldContainExactly expected
            }
        }
    }
})
