package com.razz.eva.persistence

import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import com.razz.eva.persistence.PersistenceException.ConnectionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

abstract class TransactionManager<C>(
    private val primaryProvider: ConnectionProvider<C>,
    private val replicaProvider: ConnectionProvider<C>,
) {

    open suspend fun <R> withConnection(block: suspend (Connected<C>) -> R): R {
        return when (val existingConn = ctxConnection()) {
            null -> {
                val provider = connectionProvider(currentCoroutineContext())
                var newConn: C? = null
                try {
                    newConn = acquire(provider)
                    currentCoroutineContext()[ConnectionAcquisitionCounter]?.increment()
                    block(connected(newConn, provider))
                } finally {
                    newConn?.let { provider.release(it) }
                }
            }
            // The reused connection carries the pool that opened it, so a statement inside a
            // transaction reports the truth and not an assumption about which pool that was.
            else -> block(existingConn)
        }
    }

    open suspend fun <R> inTransaction(
        mode: ConnectionMode,
        block: suspend (Connected<C>) -> R,
    ): R {
        return when (val existingConn = ctxConnection()) {
            null -> {
                check(mode == REQUIRE_NEW) { "Required existing connection but no existing connection was found" }
                var newConn: C? = null
                try {
                    newConn = acquire(primaryProvider)
                    currentCoroutineContext()[ConnectionAcquisitionCounter]?.increment()
                    val ctx = wrapConnection(connected(newConn, primaryProvider))
                    withContext(ctx) {
                        try {
                            ctx.begin()
                            val result = block(connected(newConn, primaryProvider))
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

    /**
     * The role comes from which constructor argument the provider was passed as, so a provider mislabeling
     * itself cannot mislabel a span. A provider that is neither pool gets no role. Its address remains.
     */
    private fun connected(conn: C, provider: ConnectionProvider<C>) = Connected(
        value = conn,
        endpoint = provider.endpoint,
        role = when {
            provider === primaryProvider -> PoolRole.PRIMARY
            provider === replicaProvider -> PoolRole.REPLICA
            else -> null
        },
    )

    private suspend fun acquire(provider: ConnectionProvider<C>): C = try {
        provider.acquire()
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        throw ConnectionException(ex)
    }

    private fun connectionProvider(coroutineContext: CoroutineContext) =
        if (coroutineContext[PrimaryConnectionRequiredFlag] != null) {
            primaryProvider
        } else {
            replicaProvider
        }

    abstract fun supportsPipelining(): Boolean

    protected abstract fun wrapConnection(connected: Connected<C>): ConnectionWrapper<C>

    protected abstract suspend fun ctxConnection(): Connected<C>?
}
