package com.razz.eva.test.repository

import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import com.razz.eva.persistence.TransactionManager
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.repository.TransactionalContext
import com.razz.eva.repository.TransactionalContext.Companion.transactionalContext
import io.kotest.common.ExperimentalKotest
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.spec.SpecExecutionOrder.Random
import io.kotest.core.spec.style.BehaviorSpec
import org.jooq.DSLContext
import java.time.Clock
import java.time.Clock.tickMillis
import java.time.Instant
import java.time.ZoneOffset.UTC

abstract class RepositorySpec(
    helper: RepositoryHelper,
    body: RepositorySpec.() -> Unit = {}
) : BehaviorSpec() {

    val dslContext: DSLContext = helper.dslContext
    val executor: QueryExecutor = helper.queryExecutor
    private val txnManager: TransactionManager<*> = helper.txnManager
    open val clock = Clock.fixed(tickMillis(UTC).instant(), UTC)
    val now: Instant
        get() = clock.instant()

    init {
        this.body()
    }

    suspend fun <R> inTransaction(block: suspend (context: TransactionalContext) -> R): R {
        val context = transactionalContext(clock.instant())
        val txBlock: suspend () -> R = {
            block(context)
        }
        return txnManager.inTransaction(REQUIRE_NEW, txBlock)
    }

    object ProjectKotestConfig : AbstractProjectConfig() {

        // this controls number of threads
        override val parallelism = 2

        @OptIn(ExperimentalKotest::class)
        override val concurrentSpecs = 4

        override val specExecutionOrder = Random
    }
}
