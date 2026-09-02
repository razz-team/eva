package com.razz.eva.saga

import com.razz.eva.saga.Saga.Intermediary
import com.razz.eva.saga.Saga.Terminal
import com.razz.eva.domain.Principal
import com.razz.eva.saga.OtelAttributes.SAGA_ATTEMPT
import com.razz.eva.saga.OtelAttributes.SAGA_ATTEMPTS
import com.razz.eva.saga.OtelAttributes.SAGA_PARENT_RUN_ID
import com.razz.eva.saga.OtelAttributes.SAGA_RUN_ID
import com.razz.eva.saga.OtelAttributes.SAGA_TERMINAL
import com.razz.eva.saga.OtelAttributes.UNKNOWN_TERMINAL
import com.razz.eva.saga.SagaNotification.Failed
import com.razz.eva.saga.SagaNotification.Resumed
import com.razz.eva.saga.SagaNotification.Terminated
import com.razz.eva.saga.SagaNotification.Transitioned
import com.razz.eva.tracing.getEvaTracer
import com.razz.eva.tracing.use
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds

abstract class Saga<PRINCIPAL, PARAMS, IS, TS, SELF>(
    private val sagaExecutionContext: SagaExecutionContext = sagaExecutionContext(),
    private val observers: List<SagaObserver<PRINCIPAL, PARAMS>> = listOf(),
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
        currentStep: IS?,
    ): TS? = throw ex

    protected open val sagaName: String
        get() = this::class.simpleName ?: "Saga"

    protected open suspend fun restartAfter(attempt: Int, ex: Exception): Duration? =
        RESTART_BACKOFF.takeIf { attempt < MAX_RESTARTS }

    suspend fun resume(principal: PRINCIPAL, params: PARAMS): TS {
        val sagaRun = SagaRun(SagaRunId.random(), null, 0, sagaName, principal, params)
        return startSagaRunSpan(sagaRun).use {
            advance(sagaRun, null, setOf(), System.nanoTime())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private tailrec suspend fun advance(
        sagaRun: SagaRun<PRINCIPAL, PARAMS>,
        currentStep: IS?,
        trail: Set<KClass<out Step<SELF>>>,
        startedAt: Long,
    ): TS {
        val stepStartedAt = System.nanoTime()
        val starting = currentStep == null
        val stepOutcome = if (starting) {
            recordAttempt(sagaRun)
            resolveFirst(sagaRun)
        } else {
            resolveNext(sagaRun, currentStep, trail)
        }
        return when (stepOutcome) {
            is StepOutcome.Threw -> when (val sagaOutcome = failed(sagaRun, currentStep, stepOutcome.ex)) {
                is SagaOutcome.Ended -> recordTerminal(sagaOutcome.terminal)
                is SagaOutcome.Restart -> {
                    val backoff = restartAfter(sagaRun.attempt, sagaOutcome.cause) ?: throw sagaOutcome.cause
                    val restartedSagaRun = sagaRun.copy(
                        id = SagaRunId.random(),
                        parentId = sagaRun.id,
                        attempt = sagaRun.attempt + 1,
                        sagaName = sagaName,
                    )
                    recordRestart(restartedSagaRun, sagaRun.id)
                    delay(backoff.toMillis().milliseconds)
                    advance(restartedSagaRun, null, setOf(), System.nanoTime())
                }
            }
            is StepOutcome.Resolved -> {
                val nextStep = stepOutcome.step
                val nextTrail = if (starting) setOf(nextStep::class) else trail + nextStep::class
                if (starting) {
                    notify(Resumed(sagaRun, nextStep))
                } else {
                    notify(
                        Transitioned(
                            sagaRun,
                            currentStep,
                            nextStep,
                            elapsedSince(stepStartedAt),
                        ),
                    )
                }
                when (nextStep) {
                    is Terminal<*> -> terminated(sagaRun, nextStep as TS, startedAt)
                    is Intermediary<*> -> advance(sagaRun, nextStep as IS, nextTrail, startedAt)
                }
            }
        }
    }

    private suspend fun resolveFirst(sagaRun: SagaRun<PRINCIPAL, PARAMS>): StepOutcome<Step<SELF>> =
        try {
            StepOutcome.Resolved(
                startSagaInitSpan(sagaRun.sagaName).use {
                    init(sagaRun.principal, sagaRun.params)
                },
            )
        } catch (ex: Exception) {
            StepOutcome.Threw(ex)
        }

    private suspend fun resolveNext(
        sagaRun: SagaRun<PRINCIPAL, PARAMS>,
        currentStep: IS,
        trail: Set<KClass<out Step<SELF>>>,
    ): StepOutcome<Step<SELF>> =
        try {
            val resolved = startSagaIntermediateSpan(currentStep::class.simpleName).use {
                next(sagaRun.principal, currentStep)
            }
            if (resolved::class in trail) {
                throw SagaHaltException(resolved as Intermediary<*>)
            }
            StepOutcome.Resolved(resolved)
        } catch (ex: Exception) {
            StepOutcome.Threw(ex)
        }

    private suspend fun terminated(
        sagaRun: SagaRun<PRINCIPAL, PARAMS>,
        terminal: TS,
        startedAt: Long,
    ): TS {
        recordTerminal(terminal)
        notify(Terminated(sagaRun, terminal, elapsedSince(startedAt)))
        return terminal
    }

    private suspend fun failed(
        sagaRun: SagaRun<PRINCIPAL, PARAMS>,
        step: IS?,
        ex: Exception,
    ): SagaOutcome<TS> {
        Span.current().recordException(ex)
        val mapped = try {
            onException(ex, sagaRun.principal, sagaRun.params, step)
        } catch (rethrown: Exception) {
            notify(Failed(sagaRun, step, ex, null))
            throw rethrown
        }
        notify(Failed(sagaRun, step, ex, mapped))
        return if (mapped == null) SagaOutcome.Restart(ex) else SagaOutcome.Ended(mapped)
    }

    private fun recordAttempt(sagaRun: SagaRun<PRINCIPAL, PARAMS>) {
        Span.current().setAttribute(SAGA_ATTEMPTS, (sagaRun.attempt + 1).toLong())
    }

    private fun recordTerminal(terminal: TS): TS {
        Span.current().setAttribute(SAGA_TERMINAL, terminal::class.simpleName ?: UNKNOWN_TERMINAL)
        return terminal
    }

    private fun recordRestart(restartedSagaRun: SagaRun<PRINCIPAL, PARAMS>, parentRunId: SagaRunId) {
        sagaExecutionContext.recordRestart(restartedSagaRun.sagaName)
        Span.current()
            .addEvent(
                Events.RESTART,
                Attributes.of(
                    SAGA_RUN_ID, restartedSagaRun.id.toString(),
                    SAGA_PARENT_RUN_ID, parentRunId.toString(),
                    SAGA_ATTEMPT, restartedSagaRun.attempt.toLong(),
                ),
            )
    }

    private suspend fun notify(notification: SagaNotification<PRINCIPAL, PARAMS>) {
        if (observers.isEmpty()) {
            return
        }
        val sagaName = notification.run.sagaName
        startNotifySpan(notification).use {
            observers.forEach { observer ->
                try {
                    withTimeout(sagaExecutionContext.observerTimeout.toMillis().milliseconds) {
                        observer.onNotification(notification)
                    }
                } catch (ex: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    countObserverFailure(sagaName, ex, ObserverOutcome.TIMED_OUT)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Throwable) {
                    currentCoroutineContext().ensureActive()
                    countObserverFailure(sagaName, ex, ObserverOutcome.THREW)
                }
            }
        }
    }

    private fun countObserverFailure(sagaName: String, ex: Throwable, outcome: ObserverOutcome) {
        runCatching {
            sagaExecutionContext.recordObserverFailure(sagaName, outcome)
            logger.warn(ex) { "Saga observer failed [$sagaName] [${outcome.value}]" }
        }
    }

    private fun elapsedSince(startedAtNanos: Long): Duration =
        Duration.ofNanos(System.nanoTime() - startedAtNanos)

    private fun startSagaRunSpan(sagaRun: SagaRun<PRINCIPAL, PARAMS>) =
        sagaExecutionContext.otel.getEvaTracer()
            .spanBuilder(sagaRun.sagaName)
            .setAttribute(SAGA_RUN_ID, sagaRun.id.toString())
            .startSpan()

    private fun startNotifySpan(notification: SagaNotification<PRINCIPAL, PARAMS>) =
        sagaExecutionContext.otel.getEvaTracer()
            .spanBuilder("${notification.run.sagaName}-${notification.suffix}")
            .startSpan()

    private fun startSagaInitSpan(sagaName: String) = sagaExecutionContext.otel.getEvaTracer()
        .spanBuilder("$sagaName-init")
        .startSpan()

    private fun startSagaIntermediateSpan(stepName: String?) = sagaExecutionContext.otel.getEvaTracer()
        .spanBuilder(stepName?.let { "$it-intermediate" } ?: "SagaIntermediateStep")
        .startSpan()

    private val logger = KotlinLogging.logger {}

    private companion object {
        private const val MAX_RESTARTS = 2
        private val RESTART_BACKOFF = Duration.ofMillis(100)
    }
}

private sealed interface StepOutcome<out STEP> {
    class Resolved<STEP>(val step: STEP) : StepOutcome<STEP>
    class Threw(val ex: Exception) : StepOutcome<Nothing>
}
