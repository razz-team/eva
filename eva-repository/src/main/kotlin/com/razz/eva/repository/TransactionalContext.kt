package com.razz.eva.repository

import java.time.Instant

data class TransactionalContext private constructor(val startedAt: Instant) {

    companion object {
        fun transactionalContext(startedAt: Instant): TransactionalContext {
            return TransactionalContext(startedAt)
        }
    }
}
