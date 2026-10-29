package com.razz.eva.outbox

import com.github.kagkarlsson.scheduler.Scheduler
import com.razz.eva.outbox.db.JooqOutboxStore
import com.razz.eva.outbox.kafka.BatchKafkaSender
import com.razz.eva.outbox.kafka.KafkaRecordMapper
import com.razz.eva.outbox.kafka.TopicResolver
import org.apache.kafka.clients.producer.Producer
import java.time.Duration
import java.time.InstantSource
import javax.sql.DataSource

class EvaOutbox(
    schedulerDataSource: DataSource,
    tasks: List<OutboxTaskConfig>,
    producer: Producer<String, ByteArray>,
    instantSource: InstantSource,
    schedulerTableName: String = DEFAULT_SCHEDULER_TABLE_NAME,
) : AutoCloseable {

    init {
        require(tasks.isNotEmpty()) { "at least one task is required" }
        require(schedulerTableName.isNotBlank()) { "schedulerTableName must not be blank" }
        val duplicates = tasks.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "task names must be unique, duplicated: $duplicates" }
    }

    private val outboxTasks = tasks.map { task ->
        val runner = OutboxRunner(
            store = JooqOutboxStore(task.eventsDataSource, instantSource),
            sender = BatchKafkaSender(
                producer = producer,
                mapper = KafkaRecordMapper(TopicResolver.perServiceModel(task.serviceName)),
                ackTimeout = task.outbox.ackTimeout,
                clock = instantSource,
            ),
            config = task.outbox,
        )
        OutboxTask(task.name, runner, task.outbox)
    }

    private val scheduler: Scheduler = Scheduler.create(schedulerDataSource)
        .startTasks(outboxTasks.map { it.task })
        .tableName(schedulerTableName)
        // send heartbeat every 5 seconds, default is 5 mins
        .heartbeatInterval(Duration.ofSeconds(5))
        // after 4 missed heartbeats the runner is declared dead, default is 6
        .missedHeartbeatsLimit(4)
        // check for a new task every second, default is 10s
        .pollingInterval(Duration.ofSeconds(1))
        .threads(outboxTasks.size)
        .build()

    fun start() {
        scheduler.start()
    }

    override fun close() {
        scheduler.stop()
    }

    companion object {
        const val DEFAULT_SCHEDULER_TABLE_NAME = "events.scheduled_tasks"
    }
}
