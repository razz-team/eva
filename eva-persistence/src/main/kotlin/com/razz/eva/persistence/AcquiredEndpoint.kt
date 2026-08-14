package com.razz.eva.persistence

import kotlin.coroutines.CoroutineContext

/**
 * A slot a caller puts in the coroutine context to learn which pool actually served a call.
 *
 * The transaction manager records into it at the moment it acquires, so the value describes the
 * connection that was taken rather than the one a caller predicted would be taken. Nothing is recorded
 * when no slot is present, so a caller that does not ask pays a null check, in the same way as
 * [ConnectionAcquisitionCounter].
 *
 * An absent [endpoint] therefore means no connection was acquired under this slot. Callers reporting it
 * should omit the value rather than guess, since a confidently wrong pool is worse than none.
 */
class AcquiredEndpoint : CoroutineContext.Element {

    var endpoint: DbEndpoint? = null
        private set

    internal fun record(acquired: DbEndpoint) {
        endpoint = acquired
    }

    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<AcquiredEndpoint>
}
