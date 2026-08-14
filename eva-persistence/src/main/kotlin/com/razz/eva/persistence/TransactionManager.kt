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

    open suspend fun <R> withConnection(block: suspend (C) -> R): R {
        return when (val existingConn = ctxConnection()) {
            null -> {
                val provider = connectionProvider(currentCoroutineContext())
                var newConn: C? = null
                try {
                    recordPool(provider)
                    newConn = acquire(provider)
                    currentCoroutineContext()[ConnectionAcquisitionCounter]?.increment()
                    block(newConn)
                } finally {
                    newConn?.let { provider.release(it) }
                }
            }
            else -> {
                // A context connection can only have been opened by inTransaction, which always acquires
                // from the primary, so this is an invariant rather than a guess. Without it every statement
                // after the first in a transaction would report no pool at all.
                recordPool(primaryProvider)
                block(existingConn)
            }
        }
    }

    open suspend fun <R> inTransaction(
        mode: ConnectionMode,
        block: suspend (C) -> R,
    ): R {
        return when (val existingConn = ctxConnection()) {
            null -> {
                check(mode == REQUIRE_NEW) { "Required existing connection but no existing connection was found" }
                var newConn: C? = null
                try {
                    recordPool(primaryProvider)
                    newConn = acquire(primaryProvider)
                    currentCoroutineContext()[ConnectionAcquisitionCounter]?.increment()
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
                recordPool(primaryProvider)
                block(existingConn)
            }
        }
    }

    /**
     * The role comes from which constructor argument the provider was passed as, so a provider mislabelling
     * itself cannot mislabel a span. Where both pools are the same instance the answer is PRIMARY, which is
     * honest: there is one pool.
     */
    protected suspend fun recordPool(provider: ConnectionProvider<C>) {
        val role = if (provider === primaryProvider) PoolRole.PRIMARY else PoolRole.REPLICA
        currentCoroutineContext()[AcquiredEndpoint]?.record(provider.endpoint, role)
    }

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

    protected abstract fun wrapConnection(newConn: C): ConnectionWrapper<C>

    protected abstract suspend fun ctxConnection(): C?
}
