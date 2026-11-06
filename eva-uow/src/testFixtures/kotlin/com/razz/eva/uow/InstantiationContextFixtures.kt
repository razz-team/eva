package com.razz.eva.uow

import com.razz.eva.domain.Principal

object InstantiationContextFixtures {

    val INITIAL = InstantiationContext(0)
    val AFTER_RETRY = InstantiationContext(1)

    /** The first attempt's context for params built for [principal], as the executor builds it. */
    fun initial(principal: Principal<*>): InstantiationContext = InstantiationContext(0, principal)

    /** A retry's context for params built for [principal], as the executor builds it. */
    fun afterRetry(principal: Principal<*>): InstantiationContext = InstantiationContext(1, principal)
}
