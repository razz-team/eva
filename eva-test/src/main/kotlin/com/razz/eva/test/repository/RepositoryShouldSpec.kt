package com.razz.eva.test.repository

import com.razz.eva.persistence.ConnectionMode
import com.razz.eva.persistence.TransactionManager
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.repository.TransactionalContext
import com.razz.eva.repository.TransactionalContext.Companion.transactionalContext
import io.kotest.core.spec.style.ShouldSpec
import org.jooq.DSLContext
import java.time.Clock
import java.time.Clock.tickMillis
import java.time.Instant
import java.time.ZoneOffset.UTC

abstract class RepositoryShouldSpec(
    helper: RepositoryHelper,
    body: RepositoryShouldSpec.() -> Unit = {},
) : ShouldSpec() {

    val dslContext: DSLContext = helper.dslContext
    val executor: QueryExecutor = helper.queryExecutor
    val txnManager: TransactionManager<*> = helper.txnManager
    open val clock = Clock.fixed(tickMillis(UTC).instant(), UTC)
    val now: Instant
        get() = clock.instant()

    init {
        this.body()
    }

    suspend fun <R> inTransaction(ctxInstant: Instant? = null, block: suspend (context: TransactionalContext) -> R): R {
        val context = transactionalContext(ctxInstant ?: clock.instant())
        val txBlock: suspend () -> R = {
            block(context)
        }
        return txnManager.inTransaction(ConnectionMode.REQUIRE_NEW, txBlock)
    }
}
