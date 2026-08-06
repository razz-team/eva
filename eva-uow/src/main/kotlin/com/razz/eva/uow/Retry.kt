package com.razz.eva.uow

import com.razz.eva.persistence.PersistenceException
import com.razz.eva.persistence.PersistenceException.ConnectionException
import com.razz.eva.persistence.PersistenceException.ModelRecordConstraintViolationException
import com.razz.eva.persistence.PersistenceException.StaleRecordException
import com.razz.eva.persistence.PersistenceException.UniqueModelRecordViolationException
import java.time.Duration
import java.time.Duration.ofMillis

abstract class Retry {

    abstract fun getNextDelay(currentAttempt: Int, ex: PersistenceException): Duration?

    data class StaleRecordFixedRetry(
        val attempts: Int,
        val staleRecordDelay: Duration,
    ) : Retry() {

        override fun getNextDelay(currentAttempt: Int, ex: PersistenceException): Duration? {
            return when {
                attempts <= currentAttempt -> null
                else -> when (ex) {
                    is StaleRecordException -> staleRecordDelay
                    else -> null
                }
            }
        }

        companion object {
            val DEFAULT = StaleRecordFixedRetry(1, ofMillis(100))
        }
    }

    data class UniqueViolationFixedRetry(
        val attempts: Int,
        val uniqueModelDelay: Duration,
    ) : Retry() {

        override fun getNextDelay(currentAttempt: Int, ex: PersistenceException): Duration? {
            return when {
                attempts <= currentAttempt -> null
                else -> when (ex) {
                    is UniqueModelRecordViolationException -> uniqueModelDelay
                    is ModelRecordConstraintViolationException -> uniqueModelDelay
                    else -> null
                }
            }
        }

        companion object {
            val DEFAULT = UniqueViolationFixedRetry(1, ofMillis(100))
        }
    }

    /**
     * Retries [ConnectionException]: a connection acquisition failure or a connection loss
     * witnessed by a statement. Opt-in only, deliberately without a DEFAULT: a connection lost
     * after the flush was sent is ambiguous, the transaction may have committed. Use only for
     * units whose replay is safe, that is effects are idempotent or guarded by an idempotency
     * key or a unique constraint which turns the replay into a conflict.
     */
    data class ConnectionFixedRetry(
        val attempts: Int,
        val connectionDelay: Duration,
    ) : Retry() {

        override fun getNextDelay(currentAttempt: Int, ex: PersistenceException): Duration? {
            return when {
                attempts <= currentAttempt -> null
                else -> when (ex) {
                    is ConnectionException -> connectionDelay
                    else -> null
                }
            }
        }
    }
}
