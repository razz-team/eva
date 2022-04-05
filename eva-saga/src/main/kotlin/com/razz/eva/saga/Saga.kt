package com.razz.eva.saga

import com.razz.eva.saga.Saga.Intermediary
import com.razz.eva.saga.Saga.Terminal
import com.razz.eva.uow.Principal
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

    protected abstract fun next(principal: PRINCIPAL, currentStep: IS): suspend () -> Step<SELF>

    protected open suspend fun onException(e: Exception): TS? = throw e

    suspend fun resume(principal: PRINCIPAL, params: PARAMS): TS = try {
        val initial = init(principal, params)
        run(principal, setOf(initial::class), initial)
    } catch (e: Exception) {
        onException(e) ?: resume(principal, params)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun run(
        principal: PRINCIPAL,
        trail: Set<KClass<out Step<SELF>>>,
        step: Step<SELF>
    ): TS {
        return when (step) {
            is Intermediary<*> -> next(principal, step as IS)().let {
                checkThatNot(it::class in trail) {
                    SagaHaltException(it as IS)
                }
                run(principal, trail + it::class, it)
            }
            is Terminal<*> -> step as TS
        }
    }
}
