package com.razz.eva.test.uow

import com.razz.eva.domain.Principal
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.InstantiationContextFixtures

val INITIAL = InstantiationContextFixtures.INITIAL
val AFTER_RETRY = InstantiationContextFixtures.AFTER_RETRY

fun initial(principal: Principal<*>): InstantiationContext = InstantiationContextFixtures.initial(principal)

fun afterRetry(principal: Principal<*>): InstantiationContext = InstantiationContextFixtures.afterRetry(principal)
