package com.razz.eva.outbox.app

import com.razz.eva.outbox.OutboxConfig
import com.razz.eva.outbox.kafka.KafkaSecurity
import com.razz.eva.outbox.kafka.OutboxProducer
import com.razz.eva.outbox.kafka.Sasl
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import java.time.Duration

class ConfigLoaderSpec : BehaviorSpec({

    val env = mapOf(
        "WALLETS_DB_PASSWORD" to "wallets-s3cret",
        "KAFKA_PASSWORD" to """ka"fka\s3cret: #1""",
    )

    Given("The example config") {
        val config = ConfigLoader.load(Path.of("src/dist/config.example.yaml"), env)

        Then("Placeholders are resolved, YAML special characters included") {
            config.scheduler.password shouldBe "wallets-s3cret"
            config.tasks.keys shouldBe setOf("wallets")
            config.tasks.getValue("wallets").serviceName shouldBe "wallets"
            config.tasks.getValue("wallets").database shouldBe config.scheduler
            config.kafka.security shouldBe KafkaSecurity.SaslSsl(
                Sasl.Scram(Sasl.ScramAlgorithm.SHA_512, "outbox", """ka"fka\s3cret: #1"""),
            )
        }
        Then("Omitted settings fall back to defaults") {
            config.tasks.getValue("wallets").outbox.outboxConfig shouldBe
                OutboxConfig(batchSize = 100, leeway = Duration.ofSeconds(10))
            config.schedulerTableName shouldBe "events.scheduled_tasks"
            config.kafka.compression shouldBe OutboxProducer.Compression.LZ4
            config.kafka.deliveryTimeout shouldBe null
            config.tasks.getValue("wallets").database.maximumPoolSize shouldBe null
        }
        Then("Passwords are not printed") {
            config.toString() shouldNotContain "s3cret"
        }
    }

    Given("A minimal config") {
        val config = ConfigLoader.parse(
            """
            scheduler: { jdbcUrl: "jdbc:postgresql://db/app", username: u, password: p }
            tasks:
              main:
                serviceName: kebab
                database: { jdbcUrl: "jdbc:postgresql://db/app", username: u, password: p }
            kafka: { bootstrapServers: "kafka:9092", linger: PT0.005S }
            """.trimIndent(),
            env = emptyMap(),
        )

        Then("Durations are ISO-8601 and security defaults to plaintext") {
            config.kafka.linger shouldBe Duration.ofMillis(5)
            config.kafka.security shouldBe KafkaSecurity.Plaintext
            config.tasks.getValue("main").outbox.outboxConfig shouldBe OutboxConfig()
        }
    }

    Given("A placeholder without an environment variable") {
        Then("Loading fails naming the variable") {
            val ex = shouldThrow<IllegalArgumentException> {
                ConfigLoader.load(Path.of("src/dist/config.example.yaml"), env - "KAFKA_PASSWORD")
            }
            ex.message.orEmpty() shouldContain "KAFKA_PASSWORD"
        }
    }

    Given("A config without tasks") {
        Then("Loading fails") {
            shouldThrow<IllegalArgumentException> {
                ConfigLoader.parse(
                    """
                    scheduler: { jdbcUrl: "jdbc:postgresql://db/app", username: u, password: p }
                    tasks: {}
                    kafka: { bootstrapServers: "kafka:9092" }
                    """.trimIndent(),
                    env = emptyMap(),
                )
            }
        }
    }
})
