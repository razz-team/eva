package com.razz.eva.test.saga

import com.razz.eva.domain.Principal
import com.razz.eva.saga.Saga
import io.kotest.core.spec.style.ShouldSpec
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.isAccessible
import java.time.Clock
import java.time.Clock.tickMillis
import java.time.ZoneOffset.UTC

abstract class SagaShouldSpec<PRINCIPAL, PARAMS, IS, TS, SELF>(
    body: SagaShouldSpec<PRINCIPAL, PARAMS, IS, TS, SELF>.() -> Unit,
) : ShouldSpec()
    where PRINCIPAL : Principal<*>,
          IS : Saga.Intermediary<SELF>,
          TS : Saga.Terminal<SELF>,
          SELF : Saga<PRINCIPAL, PARAMS, IS, TS, SELF> {

    private lateinit var saga: Saga<PRINCIPAL, PARAMS, IS, TS, SELF>
    fun setSaga(saga: Saga<PRINCIPAL, PARAMS, IS, TS, SELF>) {
        this.saga = saga
    }

    open val clock = Clock.fixed(tickMillis(UTC).instant(), UTC)

    private val innerInit = saga::class.functions.single { it.name == "init" }.also { it.isAccessible = true }
    private val innerNext = saga::class.functions.single { it.name == "next" }.also { it.isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    suspend fun afterParams(principal: PRINCIPAL, params: PARAMS): Saga.Step<SELF> {
        return innerInit.callSuspend(saga, principal, params) as Saga.Step<SELF>
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun afterNext(principal: PRINCIPAL, step: IS): Saga.Step<SELF> {
        return innerNext.callSuspend(saga, principal, step) as Saga.Step<SELF>
    }

    init {
        body(this)
    }
}
