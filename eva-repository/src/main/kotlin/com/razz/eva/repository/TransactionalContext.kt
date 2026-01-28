package com.razz.eva.repository

import java.time.Duration
import java.time.Instant

data class TransactionalContext private constructor(internal val startedAt: Instant) {

    operator fun plus(duration: Duration) =
        TransactionalContext(startedAt.plus(duration))

    operator fun minus(duration: Duration) =
        TransactionalContext(startedAt.minus(duration))

    companion object {
        internal fun transactionalContext(startedAt: Instant): TransactionalContext {
            return TransactionalContext(startedAt)
        }
    }
}
