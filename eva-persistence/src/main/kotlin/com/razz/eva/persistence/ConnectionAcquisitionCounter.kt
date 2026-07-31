package com.razz.eva.persistence

import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * Counts pooled connection acquisitions made while this element is present in the coroutine context.
 * The unit of work executor installs one around tryPerform, so the recorded value is the number of
 * connections the perform phase acquired: one per read in the default flush scope, zero in the
 * full unit of work scope where reads join the attempt's already-open transaction.
 */
class ConnectionAcquisitionCounter : CoroutineContext.Element {

    private val acquisitions = AtomicLong(0)

    fun increment() {
        acquisitions.incrementAndGet()
    }

    fun count(): Long = acquisitions.get()

    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<ConnectionAcquisitionCounter>
}
