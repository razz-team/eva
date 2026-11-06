package com.razz.eva.uow

import com.razz.eva.domain.Principal

/** What a params block knows of the attempt it builds params for: its number, and the principal it runs under. */
sealed class InstantiationContext(internal val attempt: Int, internal val principal: Principal<*>?) {

    internal class External(attempt: Int, principal: Principal<*>?) : InstantiationContext(attempt, principal)

    class Internal internal constructor(attempt: Int, principal: Principal<*>?) :
        InstantiationContext(attempt, principal)

    internal companion object Factory {
        internal operator fun invoke(attempt: Int): InstantiationContext = External(attempt, null)

        internal operator fun invoke(attempt: Int, principal: Principal<*>): InstantiationContext =
            External(attempt, principal)
    }
}
