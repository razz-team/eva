package com.razz.eva.uow

import com.razz.eva.domain.Principal

object InstantiationContextFixtures {

    val INITIAL = initial(TestPrincipal)
    val AFTER_RETRY = afterRetry(INITIAL)

    /** The first attempt's context of a new run, for params built for [principal], as the executor builds it. */
    fun initial(principal: Principal<*>): InstantiationContext =
        InstantiationContext(0, principal, InstantiationContext.Run())

    /** The context of a retry of [first]'s run, as the executor builds it: the same principal and run. */
    fun afterRetry(first: InstantiationContext): InstantiationContext =
        InstantiationContext(first.attempt + 1, first.principal, first.run)
}
