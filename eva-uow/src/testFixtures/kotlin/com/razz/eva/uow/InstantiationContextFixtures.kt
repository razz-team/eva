package com.razz.eva.uow

import com.razz.eva.domain.Principal

object InstantiationContextFixtures {

    val INITIAL = initial(TestPrincipal)
    val AFTER_RETRY = afterRetry(INITIAL)

    /** The first attempt's context of a new run, for params built for [principal], as the executor builds it. */
    fun initial(principal: Principal<*>): InstantiationContext =
        InstantiationContext(0, principal, InstantiationContext.Run())

    /** The context of a retry of [first]'s run, as the executor builds it: the same principal and run. */
    fun afterRetry(first: InstantiationContext): InstantiationContext {
        require(first is InstantiationContext.External) { "A retry follows a context the executor builds: use initial" }
        return InstantiationContext(first.attempt + 1, first.principal, first.run)
    }

    /**
     * This context for the attempt of [context], as the executor builds one for each attempt. A guarded unit of work
     * built with it reads the [com.razz.eva.uow.guarded.Proven] fields of params built with [context].
     */
    fun ExecutionContext.forAttempt(context: InstantiationContext): ExecutionContext = copy(attempt = context)
}
