package com.razz.eva.saga

import com.razz.eva.saga.Saga.Intermediary
import com.razz.eva.saga.Saga.Terminal
import com.razz.eva.domain.Principal
import com.razz.eva.tracing.getEvaTracer
import com.razz.eva.tracing.use
import kotlin.reflect.KClass

abstract class Saga<PRINCIPAL, PARAMS, IS, TS, SELF>(
    private val sagaExecutionContext: SagaExecutionContext = sagaExecutionContext()
)
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
        ex: Exception,
        principal: PRINCIPAL,
        params: PARAMS,
        currentStep: IS?
    ): TS? = throw ex

    suspend fun resume(principal: PRINCIPAL, params: PARAMS): TS {
        val initial = try {
            init(principal, params)
        } catch (ex: Exception) {
            onException(ex, principal, params, null) ?: resume(principal, params)
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
                val nextStep = sagaIntermediateSpan(step::class.simpleName).use {
                    next(principal, step as IS)
                }
                if (nextStep::class in trail) {
                    throw SagaHaltException(nextStep as IS)
                }
                run(principal, params, trail + nextStep::class, nextStep)
            }
            is Terminal<*> -> sagaTerminalSpan(step::class.simpleName).use {
                sagaExecutionContext.observers.forEach { sagaObserver ->
                    sagaObserver.onTerminalStep(step, principal)
                }
                step as TS
            }
        }
    } catch (ex: Exception) {
        onException(ex, principal, params, step as IS) ?: resume(principal, params)
    }

    private fun sagaIntermediateSpan(stepName: String?) = sagaExecutionContext.otel.getEvaTracer()
        .spanBuilder(stepName?.let { "$it-intermediate" } ?: "SagaIntermediateStep")
        .startSpan()

    private fun sagaTerminalSpan(stepName: String?) = sagaExecutionContext.otel.getEvaTracer()
        .spanBuilder(stepName?.let { "$it-terminal" } ?: "SagaTerminalStep")
        .startSpan()
}
