package com.razz.eva.test.uow

import com.razz.eva.domain.Principal
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.InstantiationContextFixtures

val INITIAL = InstantiationContextFixtures.INITIAL
val AFTER_RETRY = InstantiationContextFixtures.AFTER_RETRY

/**
 * The context of the first attempt of a new run under [principal], as the executor builds it, for a spec that builds
 * params only. To run the unit of work too, use `factory.firstAttempt(principal) { params }` of the spec base.
 */
fun initial(principal: Principal<*>): InstantiationContext = InstantiationContextFixtures.initial(principal)
