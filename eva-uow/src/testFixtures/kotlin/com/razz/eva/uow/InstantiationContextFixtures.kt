package com.razz.eva.uow

import com.razz.eva.domain.Principal

object InstantiationContextFixtures {

    val INITIAL = initial(TestPrincipal)
    val AFTER_RETRY = afterRetry(INITIAL)

    /** The first attempt's context of a new run, for params built for [principal], as the executor builds it. */
    fun initial(principal: Principal<*>): InstantiationContext =
        InstantiationContext.External(0, principal, InstantiationContext.Run())

    /** The context of a retry of [first]'s run, as the executor builds it: the same principal and run. */
    fun afterRetry(first: InstantiationContext): InstantiationContext {
        require(first is InstantiationContext.External) { "A retry follows a context the executor builds: use initial" }
        return InstantiationContext.External(first.attempt + 1, first.principal, first.run)
    }

    /**
     * Returns a copy of this context that carries the attempt of [attempt], as the executor builds one for each
     * attempt. A guarded unit of work built with it reads the [com.razz.eva.uow.guarded.Proven] fields of params built
     * with [attempt].
     */
    fun ExecutionContext.forAttempt(attempt: InstantiationContext): ExecutionContext = copy(attempt = attempt)
}
