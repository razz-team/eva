package com.razz.eva.test.repository

import com.razz.eva.persistence.ConnectionMode
import com.razz.eva.persistence.TransactionManager
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.repository.TransactionalContext
import com.razz.eva.repository.TransactionalContext.Companion.transactionalContext
import com.razz.eva.test.WitnessedBody
import com.razz.eva.test.runWitnessed
import io.kotest.core.spec.style.ShouldSpec
import org.jooq.DSLContext
import java.time.Clock
import java.time.Clock.tickMillis
import java.time.Instant
import java.time.ZoneOffset.UTC

/** The body runs with a witness for every model id type, so a test calls a witnessed mutator directly. */
abstract class RepositoryShouldSpec(
    helper: RepositoryHelper,
    body: WitnessedBody<RepositoryShouldSpec> = {},
) : ShouldSpec() {

    val dslContext: DSLContext = helper.dslContext
    val executor: QueryExecutor = helper.queryExecutor
    val txnManager: TransactionManager<*> = helper.txnManager
    open val clock = Clock.fixed(tickMillis(UTC).instant(), UTC)
    val now: Instant
        get() = clock.instant()

    init {
        runWitnessed(body)
    }

    suspend fun <R> inTransaction(ctxInstant: Instant? = null, block: suspend (context: TransactionalContext) -> R): R {
        val context = transactionalContext(ctxInstant ?: clock.instant())
        return txnManager.inTransaction(ConnectionMode.REQUIRE_NEW) { _ ->
            block(context)
        }
    }
}
