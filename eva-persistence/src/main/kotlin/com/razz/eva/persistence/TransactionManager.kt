package com.razz.eva.persistence

import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.tracing.PoolAttribution
import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import com.razz.eva.persistence.PersistenceException.ConnectionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

abstract class TransactionManager<C>(
    private val primaryProvider: ConnectionProvider<C>,
    private val replicaProvider: ConnectionProvider<C>,
    private val attribution: PoolAttribution = PoolAttribution.None,
) {

    open suspend fun <R> withConnection(block: suspend (C) -> R): R {
        return when (val existingConn = ctxConnection()) {
            null -> {
                val provider = connectionProvider(currentCoroutineContext())
                var newConn: C? = null
                try {
                    report(provider)
                    newConn = acquire(provider)
                    currentCoroutineContext()[ConnectionAcquisitionCounter]?.increment()
                    block(newConn)
                } finally {
                    newConn?.let { provider.release(it) }
                }
            }
            else -> {
                reportReused()
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
                    report(primaryProvider)
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
                reportReused()
                block(existingConn)
            }
        }
    }

    /**
     * Reports the pool this call goes to, before the acquire, so a call that never gets a connection still
     * says which pool starved it. The role comes from which constructor argument the provider was passed
     * as, so a provider that mislabels itself cannot mislabel the report.
     */
    private fun report(provider: ConnectionProvider<C>) {
        val role = when {
            provider === primaryProvider -> PoolRole.PRIMARY
            provider === replicaProvider -> PoolRole.REPLICA
            else -> null
        }
        val endpoint = provider.endpoint
        attribution.record(endpoint.address, endpoint.port, endpoint.database, role?.name)
    }

    /**
     * Reports the pool that opened the connection this call reuses.
     *
     * Not implemented yet. The facts belong on [ConnectionWrapper], which is per transaction and lives in
     * the coroutine context. A field on the manager would be shared by concurrent calls. Until the wrapper
     * carries them, a statement that reuses a connection reports no pool.
     */
    private fun reportReused() = Unit

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
