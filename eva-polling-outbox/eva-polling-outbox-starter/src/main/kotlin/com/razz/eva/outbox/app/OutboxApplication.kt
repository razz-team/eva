package com.razz.eva.outbox.app

import com.razz.eva.outbox.EvaOutbox
import com.razz.eva.outbox.OutboxTaskConfig
import com.razz.eva.outbox.kafka.OutboxProducer
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

class OutboxApplication(config: ApplicationConfig) : AutoCloseable {

    // closed in reverse order of creation
    private val resources = ArrayDeque<AutoCloseable>()
    private val closed = AtomicBoolean(false)
    private val outbox: EvaOutbox

    init {
        try {
            val schedulerDataSource = register(
                // a connection per running task, plus polling and heartbeats
                dataSource("outbox-scheduler", config.scheduler, defaultPoolSize = config.tasks.size + 2),
            )
            val tasks = config.tasks.map { (name, task) ->
                OutboxTaskConfig(
                    name = name,
                    serviceName = task.serviceName,
                    eventsDataSource = register(
                        dataSource("outbox-events-$name", task.database, defaultPoolSize = 2),
                    ),
                    outbox = task.outbox.outboxConfig,
                )
            }
            val kafka = config.kafka
            val producer = register(
                OutboxProducer.create(
                    bootstrapServers = kafka.bootstrapServers,
                    maxInFlightRequestsPerConnection = kafka.maxInFlightRequestsPerConnection,
                    maxRequestSize = kafka.maxRequestSize,
                    linger = kafka.linger,
                    requestTimeout = kafka.requestTimeout,
                    deliveryTimeout = kafka.deliveryTimeout ?: (kafka.requestTimeout + kafka.linger),
                    maxBlock = kafka.maxBlock,
                    clientId = kafka.clientId,
                    compression = kafka.compression,
                    security = kafka.security,
                ),
            )
            outbox = register(
                EvaOutbox(
                    schedulerDataSource = schedulerDataSource,
                    tasks = tasks,
                    producer = producer,
                    instantSource = Clock.systemUTC(),
                    schedulerTableName = config.schedulerTableName,
                ),
            )
        } catch (@Suppress("TooGenericExceptionCaught") ex: Exception) {
            close()
            throw ex
        }
    }

    fun start() {
        outbox.start()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        while (resources.isNotEmpty()) {
            val resource = resources.removeLast()
            runCatching { resource.close() }.onFailure { logger.warn(it) { "Failed to close $resource" } }
        }
    }

    private fun <T : AutoCloseable> register(resource: T): T = resource.also(resources::addLast)

    private fun dataSource(poolName: String, config: DatabaseConfig, defaultPoolSize: Int) = HikariDataSource(
        HikariConfig().apply {
            this.poolName = poolName
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            maximumPoolSize = config.maximumPoolSize ?: defaultPoolSize
        },
    )
}

fun main(args: Array<String>) {
    if (args.size != 1) {
        System.err.println("Usage: eva-polling-outbox <path to config.yaml>")
        exitProcess(2)
    }
    val config = ConfigLoader.load(Path.of(args[0]))
    val application = OutboxApplication(config)
    val stopped = CountDownLatch(1)
    Runtime.getRuntime().addShutdownHook(
        thread(start = false, name = "outbox-shutdown") {
            logger.info { "Stopping outbox" }
            application.close()
            stopped.countDown()
        },
    )
    application.start()
    logger.info { "Outbox started with tasks ${config.tasks.keys}" }
    stopped.await()
}
