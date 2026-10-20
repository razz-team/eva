package com.razz.eva.outbox

import javax.sql.DataSource

/**
 * @property name unique name of the task, part of its db-scheduler task name
 * @property serviceName service owning the events; each model gets its own topic, `<serviceName>-<modelName>`
 * @property eventsDataSource data source whose `model_events` this task fetches and marks as published
 * @property outbox fetching, publishing and retry settings of this task
 */
data class OutboxTaskConfig(
    val name: String,
    val serviceName: String,
    val eventsDataSource: DataSource,
    val outbox: OutboxConfig = OutboxConfig(),
)
