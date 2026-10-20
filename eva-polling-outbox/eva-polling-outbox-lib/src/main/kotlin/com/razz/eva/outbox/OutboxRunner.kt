package com.razz.eva.outbox

import mu.KotlinLogging
import java.time.Instant

class OutboxRunner(
    private val store: OutboxStore,
    private val sender: EventSender,
    private val config: OutboxConfig,
) {
    private val logger = KotlinLogging.logger {}

    fun run() {
        var offset = Instant.EPOCH
        var processed = 0

        try {
            for (i in 0..<config.maxIterations) {
                val batch = store.fetchUnpublished(
                    from = offset,
                    limit = config.batchSize,
                )

                if (batch.isEmpty()) {
                    logger.debug { "Batch is empty at iteration $i, rescheduling the task" }
                    return
                }

                logger.debug { "Processing ${batch.size} items at iteration $i" }
                val result = sender.send(batch)

                store.markPublished(result.acked)
                processed += result.acked.size

                val failure = result.failure
                if (failure != null) {
                    logger.debug { "Batch failed on iteration $i" }
                    throw failure
                }

                offset = result.acked.maxOf { it.insertedAt }.minus(config.leeway)
            }
        } finally {
            logger.info { "Processed $processed records" }
        }
    }
}
