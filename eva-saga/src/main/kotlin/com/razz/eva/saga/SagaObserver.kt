package com.razz.eva.saga

import com.razz.eva.domain.Principal
import com.razz.eva.saga.Saga.Step
import com.razz.eva.saga.Saga.Terminal
import java.time.Duration

sealed interface SagaNotification<PRINCIPAL, PARAMS> where PRINCIPAL : Principal<*> {

    val run: SagaRun<PRINCIPAL, PARAMS>

    val suffix: String

    class Resumed<PRINCIPAL, PARAMS>(
        override val run: SagaRun<PRINCIPAL, PARAMS>,
        val first: Step<*>,
    ) : SagaNotification<PRINCIPAL, PARAMS> where PRINCIPAL : Principal<*> {
        override val suffix = "onResumed"
    }

    class Transitioned<PRINCIPAL, PARAMS>(
        override val run: SagaRun<PRINCIPAL, PARAMS>,
        val from: Step<*>,
        val to: Step<*>,
        val elapsed: Duration,
    ) : SagaNotification<PRINCIPAL, PARAMS> where PRINCIPAL : Principal<*> {
        override val suffix = "onTransition"
    }

    class Terminated<PRINCIPAL, PARAMS>(
        override val run: SagaRun<PRINCIPAL, PARAMS>,
        val terminal: Terminal<*>,
        val elapsed: Duration,
    ) : SagaNotification<PRINCIPAL, PARAMS> where PRINCIPAL : Principal<*> {
        override val suffix = "onTerminated"
    }

    class Failed<PRINCIPAL, PARAMS>(
        override val run: SagaRun<PRINCIPAL, PARAMS>,
        val step: Step<*>?,
        val ex: Exception,
        val mappedTo: Terminal<*>?,
        val willRestart: Boolean,
        val elapsed: Duration,
    ) : SagaNotification<PRINCIPAL, PARAMS> where PRINCIPAL : Principal<*> {
        override val suffix = "onFailed"
    }
}

interface SagaObserver<PRINCIPAL, PARAMS> where PRINCIPAL : Principal<*> {

    suspend fun onNotification(notification: SagaNotification<PRINCIPAL, PARAMS>)
}
