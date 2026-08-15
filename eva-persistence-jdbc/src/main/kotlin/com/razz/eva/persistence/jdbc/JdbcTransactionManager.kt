package com.razz.eva.persistence.jdbc

import com.razz.eva.persistence.ConnectionMode
import com.razz.eva.persistence.ConnectionProvider
import com.razz.eva.persistence.Connected
import com.razz.eva.persistence.ConnectionWrapper
import com.razz.eva.persistence.TransactionManager
import com.razz.eva.tracing.getEvaMeter
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import kotlin.coroutines.coroutineContext

class JdbcTransactionManager(
    primaryProvider: ConnectionProvider<Connection>,
    replicaProvider: ConnectionProvider<Connection>,
    private val blockingJdbcContext: CoroutineDispatcher = Dispatchers.IO,
    openTelemetry: OpenTelemetry? = null,
) : TransactionManager<Connection>(primaryProvider, replicaProvider) {

    // otel default boundaries are millisecond-oriented; dispatch waits sit in the micro to
    // millisecond range, so advise boundaries covering 10us .. 1s
    private val dispatchWaitMetric = openTelemetry?.getEvaMeter()
        ?.histogramBuilder("jdbc.dispatch.wait")
        ?.setDescription("Wait between scheduling onto the blocking jdbc dispatcher and execution start")
        ?.setUnit("ns")
        ?.ofLongs()
        ?.setExplicitBucketBoundariesAdvice(DISPATCH_WAIT_BUCKET_BOUNDARIES_NANOS)
        ?.build()

    override suspend fun <R> withConnection(block: suspend (Connected<Connection>) -> R): R {
        val scheduledAt = System.nanoTime()
        return withContext(blockingJdbcContext) {
            recordDispatchWait(scheduledAt, WITH_CONNECTION)
            super.withConnection(block)
        }
    }

    override suspend fun <R> inTransaction(mode: ConnectionMode, block: suspend (Connected<Connection>) -> R): R {
        val scheduledAt = System.nanoTime()
        return withContext(blockingJdbcContext) {
            recordDispatchWait(scheduledAt, IN_TRANSACTION)
            super.inTransaction(mode, block)
        }
    }

    private fun recordDispatchWait(scheduledAt: Long, op: String) {
        dispatchWaitMetric?.record(
            System.nanoTime() - scheduledAt,
            Attributes.of(AttributeKey.stringKey(OP), op),
        )
    }

    override fun wrapConnection(connected: Connected<Connection>): ConnectionWrapper<Connection> =
        JdbcConnectionElement(connected)

    override suspend fun ctxConnection(): Connected<Connection>? =
        coroutineContext[JdbcConnectionElement]?.connected

    override fun supportsPipelining(): Boolean = false

    companion object {
        private const val OP = "op"
        private const val WITH_CONNECTION = "with_connection"
        private const val IN_TRANSACTION = "in_transaction"
        private val DISPATCH_WAIT_BUCKET_BOUNDARIES_NANOS = listOf(
            10_000L, // 10us
            50_000L,
            100_000L,
            500_000L,
            1_000_000L, // 1ms
            5_000_000L,
            10_000_000L, // 10ms
            50_000_000L,
            100_000_000L, // 100ms
            500_000_000L,
            1_000_000_000L, // 1s
        )
    }
}
