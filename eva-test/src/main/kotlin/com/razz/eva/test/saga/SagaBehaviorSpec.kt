package com.razz.eva.test.saga

import com.razz.eva.domain.Principal
import com.razz.eva.saga.Saga
import com.razz.eva.test.WitnessedBody
import com.razz.eva.test.runWitnessed
import io.kotest.core.spec.style.BehaviorSpec
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.isAccessible
import java.time.Clock
import java.time.Clock.tickMillis
import java.time.ZoneOffset.UTC

/** The body runs with a witness for every model id type, so a test calls a witnessed mutator directly. */
abstract class SagaBehaviorSpec<PRINCIPAL, PARAMS, IS, TS, SELF>(
    body: WitnessedBody<SagaBehaviorSpec<PRINCIPAL, PARAMS, IS, TS, SELF>>,
) : BehaviorSpec()
    where PRINCIPAL : Principal<*>,
          IS : Saga.Intermediary<SELF>,
          TS : Saga.Terminal<SELF>,
          SELF : Saga<PRINCIPAL, PARAMS, IS, TS, SELF> {

    private lateinit var saga: Saga<PRINCIPAL, PARAMS, IS, TS, SELF>
    private lateinit var innerInit: KFunction<*>
    private lateinit var innerNext: KFunction<*>

    fun setSaga(saga: Saga<PRINCIPAL, PARAMS, IS, TS, SELF>): Saga<PRINCIPAL, PARAMS, IS, TS, SELF> {
        this.saga = saga
        innerInit = saga::class.functions.single { it.name == "init" }.also { it.isAccessible = true }
        innerNext = saga::class.functions.single { it.name == "next" }.also { it.isAccessible = true }
        return saga
    }

    open val clock = Clock.fixed(tickMillis(UTC).instant(), UTC)

    @Suppress("UNCHECKED_CAST")
    suspend fun afterParams(principal: PRINCIPAL, params: PARAMS): Saga.Step<SELF> {
        return innerInit.callSuspend(saga, principal, params) as Saga.Step<SELF>
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun afterNext(principal: PRINCIPAL, step: IS): Saga.Step<SELF> {
        return innerNext.callSuspend(saga, principal, step) as Saga.Step<SELF>
    }

    init {
        runWitnessed(body)
    }
}
