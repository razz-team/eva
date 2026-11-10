package com.razz.eva.uow

import com.razz.eva.domain.Principal
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The receiver of a params lambda. It holds the attempt number, the principal the unit of work runs under, and the
 * run: one `execute` call with all its retries.
 */
sealed class InstantiationContext(
    internal val attempt: Int,
    internal val principal: Principal<*>,
    internal val run: Run,
) {

    internal class External(attempt: Int, principal: Principal<*>, run: Run) :
        InstantiationContext(attempt, principal, run)

    class Internal internal constructor(attempt: Int, principal: Principal<*>, run: Run) :
        InstantiationContext(attempt, principal, run)

    /** One run of a unit of work, across its attempts. Compared by identity. */
    internal class Run

    /** The context of the attempt whose `tryPerform` runs in this coroutine. The executor sets it. */
    internal class Current(val context: InstantiationContext) : AbstractCoroutineContextElement(Current) {
        companion object Key : CoroutineContext.Key<Current>
    }

    internal companion object Factory {
        internal operator fun invoke(attempt: Int, principal: Principal<*>, run: Run): InstantiationContext =
            External(attempt, principal, run)
    }
}
