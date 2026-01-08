package com.razz.eva.persistence

import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

abstract class TransactionManager<C>(
    private val primaryProvider: ConnectionProvider<C>,
    private val replicaProvider: ConnectionProvider<C>,
) {

    open suspend fun <R> withConnection(block: suspend (C) -> R): R {
        return when (val existingConn = ctxConnection()) {
            null -> {
                var newConn: C? = null
                try {
                    newConn = connectionProvider(currentCoroutineContext()).acquire()
                    block(newConn)
                } finally {
                    newConn?.let { connectionProvider(currentCoroutineContext()).release(it) }
                }
            }
            else -> block(existingConn)
        }
    }

    suspend fun <R> inTransaction(
        mode: ConnectionMode,
        block: suspend () -> R
    ): R {
        return inTransaction(mode) { _ -> block() }
    }

    open suspend fun <R> inTransaction(
        mode: ConnectionMode,
        block: suspend (C) -> R
    ): R {
        return when (val existingConn = ctxConnection()) {
            null -> {
                check(mode == REQUIRE_NEW) { "Required existing connection but no existing connection was found" }
                var newConn: C? = null
                try {
                    newConn = primaryProvider.acquire()
                    val ctx = wrapConnection(newConn)
                    withContext(ctx) {
                        try {
                            ctx.begin()
                            val result = block(newConn)
                            ctx.commit()
                            result
                        } catch (ex: Exception) {
                            ctx.rollback()
                            throw ex
                        }
                    }
                } finally {
                    newConn?.let { primaryProvider.release(it) }
                }
            }
            // we do not commit/rollback/release existingConn after calling block
            // because we are in recursive call to inTransaction
            // and this connection was created upwards the callstack during first call to inTransaction
            // and will be handled there
            else -> {
                check(mode == REQUIRE_EXISTING) { "Required new connection but existing connection was found" }
                block(existingConn)
            }
        }
    }

    private fun connectionProvider(coroutineContext: CoroutineContext) =
        if (coroutineContext[PrimaryConnectionRequiredFlag] != null) {
            primaryProvider
        } else {
            replicaProvider
        }

    abstract fun supportsPipelining(): Boolean

    protected abstract fun wrapConnection(newConn: C): ConnectionWrapper<C>

    protected abstract suspend fun ctxConnection(): C?
}
