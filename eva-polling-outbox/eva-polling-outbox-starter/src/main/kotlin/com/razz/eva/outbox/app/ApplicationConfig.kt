package com.razz.eva.outbox.app

import com.razz.eva.outbox.EvaOutbox
import com.razz.eva.outbox.OutboxConfig
import com.razz.eva.outbox.kafka.KafkaSecurity
import com.razz.eva.outbox.kafka.OutboxProducer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.Duration

/**
 * @property scheduler database holding db-scheduler's table, see [schedulerTableName]
 * @property schedulerTableName see [EvaOutbox]
 * @property tasks one outbox task per events database, keyed by task name
 */
@Serializable
data class ApplicationConfig(
    val scheduler: DatabaseConfig,
    val schedulerTableName: String = EvaOutbox.DEFAULT_SCHEDULER_TABLE_NAME,
    val tasks: Map<String, TaskConfig>,
    val kafka: KafkaConfig,
) {
    init {
        require(tasks.isNotEmpty()) { "at least one task is required" }
    }
}

/**
 * [OutboxConfig] as it is read from the config file, see it for the meaning and defaults of the settings;
 * durations are ISO-8601, e.g. `PT10S`.
 */
@Serializable
data class OutboxSettings(
    val batchSize: Int = DEFAULTS.batchSize,
    val maxIterations: Int = DEFAULTS.maxIterations,
    @Serializable(with = IsoDurationSerializer::class)
    val leeway: Duration = DEFAULTS.leeway,
    @Serializable(with = IsoDurationSerializer::class)
    val ackTimeout: Duration = DEFAULTS.ackTimeout,
    @Serializable(with = IsoDurationSerializer::class)
    val minBackoff: Duration = DEFAULTS.minBackoff,
    @Serializable(with = IsoDurationSerializer::class)
    val maxBackoff: Duration = DEFAULTS.maxBackoff,
) {
    // validated while the config is read rather than once the application is wired
    @Transient
    val outboxConfig = OutboxConfig(
        batchSize = batchSize,
        maxIterations = maxIterations,
        leeway = leeway,
        ackTimeout = ackTimeout,
        minBackoff = minBackoff,
        maxBackoff = maxBackoff,
    )

    private companion object {
        val DEFAULTS = OutboxConfig()
    }
}

/**
 * @property maximumPoolSize defaults to what the outbox needs for the role of the database
 */
@Serializable
data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int? = null,
) {
    override fun toString() =
        "DatabaseConfig(jdbcUrl=$jdbcUrl, username=$username, password=***, maximumPoolSize=$maximumPoolSize)"
}

/**
 * @property serviceName service owning the events; each model gets its own topic, `<serviceName>-<modelName>`
 */
@Serializable
data class TaskConfig(
    val serviceName: String,
    val database: DatabaseConfig,
    val outbox: OutboxSettings = OutboxSettings(),
)

/**
 * Producer settings, see [OutboxProducer.create] for their meaning; durations are ISO-8601, e.g. `PT10S`.
 *
 * @property deliveryTimeout defaults to [requestTimeout] plus [linger]
 */
@Serializable
data class KafkaConfig(
    val bootstrapServers: String,
    val clientId: String = "eva-polling-outbox",
    val maxInFlightRequestsPerConnection: Int = 5,
    val maxRequestSize: Int = 2 * 1024 * 1024,
    @Serializable(with = IsoDurationSerializer::class)
    val linger: Duration = Duration.ofMillis(10),
    @Serializable(with = IsoDurationSerializer::class)
    val requestTimeout: Duration = Duration.ofSeconds(10),
    @Serializable(with = IsoDurationSerializer::class)
    val deliveryTimeout: Duration? = null,
    @Serializable(with = IsoDurationSerializer::class)
    val maxBlock: Duration = Duration.ofSeconds(10),
    val compression: OutboxProducer.Compression = OutboxProducer.Compression.LZ4,
    val security: KafkaSecurity = KafkaSecurity.Plaintext,
)
