package com.razz.eva.outbox

import com.github.kagkarlsson.scheduler.task.ExecutionComplete
import com.github.kagkarlsson.scheduler.task.ExecutionOperations
import com.github.kagkarlsson.scheduler.task.FailureHandler
import java.time.Duration
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Retries a failed execution after [initial] * [multiplier]^consecutiveFailures, but never later than [max].
 * db-scheduler's own ExponentialBackoffFailureHandler has no upper bound.
 */
class CappedExponentialBackoff<T>(
    private val initial: Duration,
    private val max: Duration,
    private val multiplier: Double = 2.0,
) : FailureHandler<T> {

    init {
        require(initial.isPositive) { "initial must be positive" }
        require(max >= initial) { "max must not be less than initial" }
        require(multiplier >= 1.0) { "multiplier must be at least 1" }
    }

    override fun onFailure(complete: ExecutionComplete, ops: ExecutionOperations<T>) {
        val delay = calculateDelay(complete.execution.consecutiveFailures)
        ops.reschedule(complete, complete.timeDone + delay)
    }

    internal fun calculateDelay(consecutiveFailures: Int): Duration {
        // a huge exponent gives infinity, which rounds to Long.MAX_VALUE millis and is then capped
        val backoffMillis = initial.toMillis() * multiplier.pow(consecutiveFailures)
        return minOf(Duration.ofMillis(backoffMillis.roundToLong()), max)
    }
}
