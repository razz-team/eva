package com.razz.eva.saga

import com.razz.eva.saga.Saga.Intermediary
import com.razz.eva.saga.Saga.Terminal
import com.razz.eva.domain.Principal
import kotlin.reflect.KClass

abstract class Saga<PRINCIPAL, PARAMS, IS, TS, SELF>
    where PRINCIPAL : Principal<*>,
          IS : Intermediary<SELF>,
          TS : Terminal<SELF>,
          SELF : Saga<PRINCIPAL, PARAMS, IS, TS, SELF> {

    class SagaHaltException(step: Intermediary<*>) :
        IllegalStateException("Saga step [${step::class.simpleName}] already seen")

    sealed interface Step<SAGA>
        where SAGA : Saga<*, *, out Intermediary<SAGA>, out Terminal<SAGA>, SAGA>
    interface Intermediary<SAGA> : Step<SAGA>
        where SAGA : Saga<*, *, out Intermediary<SAGA>, out Terminal<SAGA>, SAGA>
    interface Terminal<SAGA> : Step<SAGA>
        where SAGA : Saga<*, *, out Intermediary<SAGA>, out Terminal<SAGA>, SAGA>

    protected abstract suspend fun init(principal: PRINCIPAL, params: PARAMS): Step<SELF>

    protected abstract suspend fun next(principal: PRINCIPAL, currentStep: IS): Step<SELF>

    protected open suspend fun onException(
        e: Exception,
        principal: PRINCIPAL,
        params: PARAMS,
        currentStep: IS?
    ): TS? = throw e

    suspend fun resume(principal: PRINCIPAL, params: PARAMS): TS {
        val initial = try {
            init(principal, params)
        } catch (e: Exception) {
            onException(e, principal, params, null) ?: resume(principal, params)
        }
        return run(principal, params, setOf(initial::class), initial)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun run(
        principal: PRINCIPAL,
        params: PARAMS,
        trail: Set<KClass<out Step<SELF>>>,
        step: Step<SELF>
    ): TS = try {
        when (step) {
            is Intermediary<*> -> {
                val nextStep = next(principal, step as IS)
                if (nextStep::class in trail) {
                    throw SagaHaltException(nextStep as IS)
                }
                run(principal, params, trail + nextStep::class, nextStep)
            }
            is Terminal<*> -> step as TS
        }
    } catch (e: Exception) {
        onException(e, principal, params, step as IS) ?: resume(principal, params)
    }
}
