package com.razz.eva.test.uow

import com.razz.eva.domain.Principal
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.InstantiationContextFixtures
import com.razz.eva.uow.InstantiationContextFixtures.forAttempt as attemptOf

val INITIAL = InstantiationContextFixtures.INITIAL
val AFTER_RETRY = InstantiationContextFixtures.AFTER_RETRY

/** The context of the first attempt of a new run under [principal], as the executor builds it. */
fun initial(principal: Principal<*>): InstantiationContext = InstantiationContextFixtures.initial(principal)

/** The context of a retry of the run of [first], as the executor builds it: the same principal and run. */
fun afterRetry(first: InstantiationContext): InstantiationContext = InstantiationContextFixtures.afterRetry(first)

/**
 * Returns a copy of this context that carries the attempt of [attempt], as the executor builds one for each attempt.
 * A guarded unit of work reads a `Proven` field only in the attempt that built its params, so a spec that calls
 * `tryPerform` itself builds the unit of work with this context:
 * `Uow(executionContext.forAttempt(first)).tryPerform(principal, first.params())`.
 */
fun ExecutionContext.forAttempt(attempt: InstantiationContext): ExecutionContext = attemptOf(attempt)
