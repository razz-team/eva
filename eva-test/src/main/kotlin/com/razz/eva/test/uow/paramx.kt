package com.razz.eva.test.uow

import com.razz.eva.domain.Principal
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.InstantiationContextFixtures
import com.razz.eva.uow.InstantiationContextFixtures.inAttempt

val INITIAL = InstantiationContextFixtures.INITIAL
val AFTER_RETRY = InstantiationContextFixtures.AFTER_RETRY

/** The context of the first attempt of a new run under [principal], as the executor builds it. */
fun initial(principal: Principal<*>): InstantiationContext = InstantiationContextFixtures.initial(principal)

/** The context of a retry of the run of [first], as the executor builds it: the same principal and run. */
fun afterRetry(first: InstantiationContext): InstantiationContext = InstantiationContextFixtures.afterRetry(first)

/**
 * Runs [block] in the attempt of [context], as the executor runs `tryPerform`. A guarded unit of work reads a
 * `Proven` field only in the run that built its params, so a spec that calls `tryPerform` itself calls it in here:
 * `inAttempt(first) { uow.tryPerform(principal, first.params()) }`.
 */
suspend fun <T> inAttempt(context: InstantiationContext, block: suspend () -> T): T = context.inAttempt(block)
