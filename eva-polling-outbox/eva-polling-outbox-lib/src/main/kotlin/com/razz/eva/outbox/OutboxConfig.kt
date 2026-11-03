package com.razz.eva.outbox

import java.time.Duration

/**
 * @property batchSize max number of events fetched and published per iteration
 * @property maxIterations max number of fetch-publish iterations per task execution,
 *   after which the task is put back into the scheduler queue
 * @property leeway how far back from the latest seen `inserted_at` the 2nd and later iterations look;
 *   covers transactions that commit after rows with a later `inserted_at` became visible
 * @property ackTimeout max time to wait for the broker acks of a batch
 * @property minBackoff delay before retrying after the first failed execution,
 *   doubled on each consecutive failure
 * @property maxBackoff upper bound for the retry delay
 */
data class OutboxConfig(
    val batchSize: Int = 100,
    val maxIterations: Int = 10,
    val leeway: Duration = Duration.ofSeconds(10),
    val ackTimeout: Duration = Duration.ofSeconds(30),
    val minBackoff: Duration = Duration.ofSeconds(1),
    val maxBackoff: Duration = Duration.ofMinutes(1),
) {
    init {
        require(batchSize > 0) { "batchSize must be positive" }
        require(maxIterations > 0) { "maxIterations must be positive" }
        require(!leeway.isNegative) { "leeway must not be negative" }
        require(minBackoff.isPositive) { "minBackoff must be positive" }
        require(maxBackoff >= minBackoff) { "maxBackoff must not be less than minBackoff" }
    }
}
