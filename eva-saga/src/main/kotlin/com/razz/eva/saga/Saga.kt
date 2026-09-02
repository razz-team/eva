package com.razz.eva.saga

import com.razz.eva.saga.Saga.Intermediary
import com.razz.eva.saga.Saga.Terminal
import com.razz.eva.domain.Principal
import com.razz.eva.saga.OtelAttributes.SAGA_ATTEMPT
import com.razz.eva.saga.OtelAttributes.SAGA_PARENT_RUN_ID
import com.razz.eva.saga.OtelAttributes.SAGA_RUN_ID
import com.razz.eva.saga.OtelAttributes.SAGA_TERMINAL
import com.razz.eva.saga.OtelAttributes.UNKNOWN_TERMINAL
import com.razz.eva.saga.SagaOutcome.Ended
import com.razz.eva.saga.SagaOutcome.Restart
import com.razz.eva.tracing.getEvaTracer
import com.razz.eva.tracing.use
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode.ERROR
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
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
        var currentId = SagaRunId.random()
        var currentParentId: SagaRunId? = null
        var attempt = 0
        while (true) {
            val name = sagaName
            val sagaRun = SagaRun(currentId, currentParentId, name, principal, params)
            val outcome = startSagaRunSpan(sagaRun, attempt).use { runOnce(sagaRun) }
            when (outcome) {
                is Ended -> {
                    return outcome.terminal
                }
                is Restart -> {
                    val backoff = restartAfter(attempt, outcome.cause) ?: throw outcome.cause
                    sagaExecutionContext.recordRestart(name)
                    delay(backoff.toMillis().milliseconds)
                    attempt += 1
                    currentParentId = currentId
                    currentId = SagaRunId.random()
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun runOnce(sagaRun: SagaRun<PRINCIPAL, PARAMS>): SagaOutcome<TS> {
        val startedAt = System.nanoTime()
        var step = try {
            startSagaInitSpan(sagaRun.sagaName).use { init(sagaRun.principal, sagaRun.params) }
        } catch (ex: Exception) {
            return failed(sagaRun, null, ex)
        }
        emit(sagaRun.sagaName, Notification.RESUMED) { it.onResumed(sagaRun, step) }
        var trail = setOf(step::class)
        while (true) {
            when (val current = step) {
                is Intermediary<*> -> {
                    val currentStep = current as IS
                    val stepStartedAt = System.nanoTime()
                    val nextStep = try {
                        val resolved = startSagaIntermediateSpan(currentStep::class.simpleName).use {
                            next(sagaRun.principal, currentStep)
                        }
                        if (resolved::class in trail) {
                            throw SagaHaltException(resolved as Intermediary<*>)
                        }
                        resolved
                    } catch (ex: Exception) {
                        return failed(sagaRun, currentStep, ex)
                    }
                    emit(sagaRun.sagaName, Notification.TRANSITION) {
                        it.onTransition(sagaRun, currentStep, nextStep, elapsedSince(stepStartedAt))
                    }
                    trail = trail + nextStep::class
                    step = nextStep
                }
                is Terminal<*> -> {
                    Span.current().setAttribute(SAGA_TERMINAL, current::class.simpleName ?: UNKNOWN_TERMINAL)
                    emit(sagaRun.sagaName, Notification.TERMINATED) {
                        it.onTerminated(sagaRun, current, elapsedSince(startedAt))
                    }
                    return Ended(current as TS)
                }
            }
        }
    }

    private suspend fun failed(
        sagaRun: SagaRun<PRINCIPAL, PARAMS>,
        step: IS?,
        ex: Exception,
    ): SagaOutcome<TS> {
        Span.current().recordException(ex).setStatus(ERROR)
        val mapped = try {
            onException(ex, sagaRun.principal, sagaRun.params, step)
        } catch (rethrown: Exception) {
            emit(sagaRun.sagaName, Notification.FAILED) { it.onFailed(sagaRun, step, ex, null) }
            throw rethrown
        }
        emit(sagaRun.sagaName, Notification.FAILED) { it.onFailed(sagaRun, step, ex, mapped) }
        return if (mapped != null) Ended(mapped) else Restart(ex)
    }

    private suspend fun emit(
        sagaName: String,
        notification: Notification,
        notify: suspend (SagaObserver<PRINCIPAL, PARAMS>) -> Unit,
    ) {
        if (observers.isEmpty()) {
            return
        }
        startNotifySpan(sagaName, notification).use {
            notifyEach(sagaName, notify)
        }
    }

    private suspend fun notifyEach(
        sagaName: String,
        notify: suspend (SagaObserver<PRINCIPAL, PARAMS>) -> Unit,
    ) {
        observers.forEach { observer ->
            try {
                withTimeout(sagaExecutionContext.observerTimeout.toMillis().milliseconds) { notify(observer) }
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

    private fun countObserverFailure(sagaName: String, ex: Throwable, outcome: ObserverOutcome) {
        runCatching {
            sagaExecutionContext.recordObserverFailure(sagaName, outcome)
            logger.warn(ex) { "Saga observer failed [$sagaName] [${outcome.value}]" }
        }
    }

    private fun elapsedSince(startedAtNanos: Long): Duration =
        Duration.ofNanos(System.nanoTime() - startedAtNanos)

    private fun startSagaRunSpan(sagaRun: SagaRun<PRINCIPAL, PARAMS>, attempt: Int) =
        sagaExecutionContext.otel.getEvaTracer()
            .spanBuilder(sagaRun.sagaName)
            .setAttribute(SAGA_RUN_ID, sagaRun.id.toString())
            .setAttribute(SAGA_ATTEMPT, attempt.toLong())
            .apply { sagaRun.parentId?.let { setAttribute(SAGA_PARENT_RUN_ID, it.toString()) } }
            .startSpan()

    private fun startNotifySpan(sagaName: String, notification: Notification) =
        sagaExecutionContext.otel.getEvaTracer()
            .spanBuilder("$sagaName-${notification.suffix}")
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
