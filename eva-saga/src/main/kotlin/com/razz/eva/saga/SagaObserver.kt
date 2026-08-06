package com.razz.eva.saga

interface SagaObserver {

    fun onTerminalStep(step: Saga.Terminal<*>, principal: Any)
}
