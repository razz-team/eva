package com.razz.eva.uow

object InstantiationContextFixtures {

    val INITIAL: InstantiationContext = InstantiationContext.External(0)
    val AFTER_RETRY: InstantiationContext = InstantiationContext.External(1)
}
