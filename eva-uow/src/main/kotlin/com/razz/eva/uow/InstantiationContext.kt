package com.razz.eva.uow

import com.razz.eva.domain.Principal

/**
 * What a params block knows of the attempt it builds params for: its number, the principal it runs under, and the
 * run it belongs to, one per `execute` call and shared by its retries.
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

    internal companion object Factory {
        internal operator fun invoke(attempt: Int, principal: Principal<*>, run: Run): InstantiationContext =
            External(attempt, principal, run)
    }
}
