package com.razz.eva.outbox

import com.razz.eva.migrations.Migration.Factory.EventsMigration
import com.razz.eva.migrations.Migrations
import com.razz.eva.outbox.db.JooqOutboxStore
import com.razz.eva.outbox.kafka.KafkaRecordMapper
import com.razz.eva.outbox.kafka.OutboxProducer
import com.razz.eva.outbox.kafka.SequentialKafkaSender
import com.razz.eva.outbox.kafka.TopicResolver
import com.razz.eva.test.db.DatabaseContainer
import com.razz.eva.test.db.DatabaseContainerHelper
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import org.testcontainers.kafka.ConfluentKafkaContainer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.InstantSource
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.MICROS
import java.util.UUID
import java.util.UUID.randomUUID
import kotlin.time.Duration.Companion.seconds

class EvaOutboxSpec : BehaviorSpec({

    val db = DatabaseContainerHelper.create("eva_outbox_test")
    val dataSource = DatabaseContainer.BASIC.localPool(db.dbName(), 4)
    val dsl = DSL.using(dataSource, POSTGRES)

    val kafka = ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0").apply { start() }
    val producer = OutboxProducer.create(bootstrapServers = kafka.bootstrapServers)
    afterSpec {
        producer.close()
        kafka.stop()
    }

    fun insertEvent(insertedAt: Instant, modelId: String = "model-1", db: DSLContext = dsl): UUID {
        val id = randomUUID()
        val insertedAtUtc = LocalDateTime.ofInstant(insertedAt, UTC)
        db.execute(
            """
            INSERT INTO events.model_events (id, uow_id, model_id, name, model_name, occurred_at, inserted_at, payload)
            VALUES (?, ?, ?, 'SomethingHappened', 'Model', ?, ?, ?)
            """.trimIndent(),
            id, randomUUID(), modelId, insertedAtUtc, insertedAtUtc, """{"id":"$id"}""",
        )
        return id
    }

    fun unpublished(db: DSLContext = dsl): List<UUID> = db
        .fetch("SELECT id FROM events.model_events WHERE published_at IS NULL ORDER BY inserted_at")
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

    fun ConsumerRecord<String, ByteArray>.eventId() =
        UUID.fromString(Json.parseToJsonElement(String(value())).jsonObject.getValue("id").jsonPrimitive.content)

    // model_events keeps microseconds
    val now = Instant.now().truncatedTo(MICROS)

    // migrate up to the version before model_events.published_at, to insert an event the outbox has not seen
    Flyway.configure()
        .dataSource(dataSource)
        .schemas(EventsMigration.schema.stringValue())
        .locations(*EventsMigration.classpathLocations().toTypedArray())
        .target("2")
        .load()
        .migrate()
    val legacyEvent = insertEvent(now.minus(Duration.ofDays(1)))
    Migrations(dataSource, EventsMigration, *arrayOf()).start()

    Given("Events inserted before the outbox was installed") {
        Then("They are treated as published") {
            unpublished().shouldBeEmpty()
            dsl.select(DSL.field("published_at", LocalDateTime::class.java))
                .from("events.model_events")
                .where(DSL.field("id", UUID::class.java).eq(legacyEvent))
                .fetchSingle()
                .value1() shouldBe LocalDateTime.of(1970, 1, 1, 0, 0)
        }
    }

    Given("Unpublished events and a runner with a small iteration budget") {
        val topic = "runner-test"
        val ids = (0L until 5L).map { insertEvent(now.plusSeconds(it), modelId = "model-$it") }
        val publishedAt = Instant.parse("2026-01-02T03:04:05.123456Z")
        val runner = OutboxRunner(
            store = JooqOutboxStore(dataSource, InstantSource.fixed(publishedAt)),
            sender = SequentialKafkaSender(
                producer,
                KafkaRecordMapper(TopicResolver.single(topic)),
                Duration.ofSeconds(30),
            ),
            config = OutboxConfig(batchSize = 2, maxIterations = 2),
        )

        When("Runner runs twice") {
            runner.run()
            val unpublishedAfterFirst = unpublished()
            runner.run()

            Then("First run stops at the iteration limit, second one publishes the rest") {
                unpublishedAfterFirst shouldContainExactly ids.drop(4)
                unpublished().shouldBeEmpty()
            }
            Then("Events are published in order, keyed by model id") {
                val records = consume(topic, ids.size)
                records.map { it.eventId() } shouldContainExactly ids
                records.map { it.key() } shouldContainExactly (0 until 5).map { "model-$it" }
            }
            Then("Published_at is taken from the instant source, in UTC") {
                val expected = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123_456_000)
                dsl.select(DSL.field("published_at", LocalDateTime::class.java))
                    .from("events.model_events")
                    .where(DSL.field("id", UUID::class.java).`in`(ids))
                    .fetch { it.value1() } shouldContainExactly List(ids.size) { expected }
            }
        }

        When("A straggler older than the published events shows up") {
            val straggler = insertEvent(now.minus(Duration.ofHours(1)))
            runner.run()

            Then("It is picked up by the unfiltered first iteration") {
                unpublished().shouldBeEmpty()
            }
            Then("Published_at guard prevents marking it twice") {
                val event = outboxEvent(insertedAt = now.minus(Duration.ofHours(1)), id = straggler)
                JooqOutboxStore(dataSource, Clock.systemUTC()).markPublished(listOf(event)) shouldBe 0
            }
        }
    }

    Given("Running outbox with a task per events database") {
        val otherDb = DatabaseContainerHelper.create("eva_outbox_test_other")
        val otherDataSource = DatabaseContainer.BASIC.localPool(otherDb.dbName(), 4)
        val otherDsl = DSL.using(otherDataSource, POSTGRES)
        Migrations(otherDataSource, EventsMigration, *arrayOf()).start()

        val outbox = EvaOutbox(
            schedulerDataSource = dataSource,
            tasks = listOf(
                OutboxTaskConfig("main", "kebab", dataSource),
                OutboxTaskConfig("other", "wallets", otherDataSource),
            ),
            producer = producer,
            instantSource = Clock.systemUTC(),
        )
        outbox.start()
        afterContainer { outbox.close() }

        When("Events are inserted into both databases") {
            val ids = (0L until 3L).map { insertEvent(Instant.now().plusSeconds(it)) }
            val otherIds = (0L until 3L).map { insertEvent(Instant.now().plusSeconds(it), db = otherDsl) }

            Then("Each task publishes the events of its own database to its service's model topics") {
                eventually(30.seconds) {
                    unpublished().shouldBeEmpty()
                    unpublished(otherDsl).shouldBeEmpty()
                }
                consume("kebab-Model", ids.size).map { it.eventId() } shouldContainExactlyInAnyOrder ids
                consume("wallets-Model", otherIds.size).map { it.eventId() } shouldContainExactlyInAnyOrder otherIds
            }
        }
    }

    Given("Tasks with the same name") {
        Then("Outbox is rejected") {
            shouldThrow<IllegalArgumentException> {
                EvaOutbox(
                    schedulerDataSource = dataSource,
                    tasks = listOf(
                        OutboxTaskConfig("main", "kebab", dataSource),
                        OutboxTaskConfig("main", "wallets", dataSource),
                    ),
                    producer = producer,
                    instantSource = Clock.systemUTC(),
                )
            }
        }
    }
})
