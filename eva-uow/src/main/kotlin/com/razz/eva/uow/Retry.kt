package com.razz.eva.uow

import com.razz.eva.persistence.PersistenceException
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
}
