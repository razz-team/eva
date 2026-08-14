package com.razz.eva.persistence

import kotlin.coroutines.CoroutineContext

/**
 * A slot a caller puts in the coroutine context to learn which pool actually served a call.
 *
 * The transaction manager records into it as it goes to a pool, so the value describes the pool the call
 * went to. A caller does not predict it. The record happens before the acquire, so a call that
 * fails to get a connection still says which pool it was starved on, which is the case the attribute exists
 * for. Nothing is recorded when no slot is present, and the provider is not even consulted for its
 * endpoint. Unlike [ConnectionAcquisitionCounter], which counts after a connection comes back, this is
 * written before the attempt, so the two do not agree on a failed acquire.
 *
 * An absent [endpoint] means no pool was reached under this slot. Callers reporting it should omit the
 * value. A confidently wrong pool is worse than none.
 *
 * One slot serves one call, and a query executor allocates its own and does not read a caller's, so
 * placing a slot at request scope observes nothing. Fields are volatile because a manager may record on a
 * different thread from the one that reads them; a slot shared between concurrent calls would report
 * whichever wrote last.
 */
class AcquiredEndpoint : CoroutineContext.Element {

    @Volatile
    var endpoint: DbEndpoint? = null
        private set

    @Volatile
    var role: PoolRole? = null
        private set

    internal fun record(acquired: DbEndpoint, acquiredRole: PoolRole?) {
        endpoint = acquired
        role = acquiredRole
    }

    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<AcquiredEndpoint>
}
