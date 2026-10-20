package com.razz.eva.outbox

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay
import mu.KotlinLogging

class OutboxTask(
    private val name: String,
    private val runner: OutboxRunner,
    private val config: OutboxConfig,
) {
    private val logger = KotlinLogging.logger {}

    // the next execution starts a second after the previous one has finished
    val task: RecurringTask<Void> = Tasks.recurring("$TASK_NAME_PREFIX$name", FixedDelay.ofSeconds(1))
        .onFailure(CappedExponentialBackoff(config.minBackoff, config.maxBackoff))
        .execute { _, _ ->
            runCatching { runner.run() }.onFailure { logger.error(it) { "Outbox $name execution failed" } }.getOrThrow()
        }

    companion object {
        const val TASK_NAME_PREFIX = "eva-outbox-publish-"
    }
}
