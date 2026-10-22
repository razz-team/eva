package com.razz.eva.saga

import com.razz.eva.saga.SagaNotification.Failed
import com.razz.eva.saga.SagaNotification.Resumed
import com.razz.eva.saga.SagaNotification.Terminated
import com.razz.eva.saga.SagaNotification.Transitioned
import com.razz.eva.saga.TestSaga.Params
import com.razz.eva.saga.TestSaga.TestPrincipal
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import kotlinx.coroutines.delay
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class RecordingObserver : SagaObserver<TestPrincipal, Params> {

    val events = mutableListOf<String>()
    val runIds = mutableListOf<SagaRunId>()
    val roots = mutableListOf<SagaRunId>()
    val parents = mutableListOf<Pair<SagaRunId, SagaRunId?>>()
    val sagaNames = mutableListOf<String>()
    val stepElapsed = mutableListOf<Duration>()
    val failureElapsed = mutableListOf<Duration>()
    val failureRunElapsed = mutableListOf<Duration>()
    val terminalElapsed = mutableListOf<Pair<Duration, Duration>>()
    val failureWillRestart = mutableListOf<Boolean>()

    override suspend fun onNotification(notification: SagaNotification<TestPrincipal, Params>) {
        val run = notification.run
        runIds += run.id
        roots += run.rootId
        events += when (notification) {
            is Resumed -> {
                parents += run.id to run.parentId
                sagaNames += run.sagaName
                stepElapsed += notification.stepElapsed
                "resumed:${notification.first::class.simpleName}"
            }
            is Transitioned -> {
                stepElapsed += notification.stepElapsed
                "transition:${notification.from::class.simpleName}->${notification.to::class.simpleName}"
            }
            is Terminated -> {
                terminalElapsed += notification.attemptElapsed to notification.runElapsed
                "terminated:${notification.terminal::class.simpleName}"
            }
            is Failed -> {
                failureElapsed += notification.attemptElapsed
                failureRunElapsed += notification.runElapsed
                failureWillRestart += notification.willRestart
                val stepName = notification.step?.let { it::class.simpleName }
                val mappedName = notification.mappedTo?.let { it::class.simpleName }
                "failed:$stepName:${notification.ex::class.simpleName}:$mappedName"
            }
        }
    }
}

internal class ThrowingObserver(private val failure: () -> Throwable) : SagaObserver<TestPrincipal, Params> {

    override suspend fun onNotification(notification: SagaNotification<TestPrincipal, Params>): Unit =
        throw failure()
}

internal class SlowObserver(private val takes: Duration) : SagaObserver<TestPrincipal, Params> {

    override suspend fun onNotification(notification: SagaNotification<TestPrincipal, Params>) =
        delay(takes.toMillis().milliseconds)
}

internal class TwoStepObserver(private val stalls: Duration) : SagaObserver<TestPrincipal, Params> {

    val applied = mutableListOf<String>()

    override suspend fun onNotification(notification: SagaNotification<TestPrincipal, Params>) {
        if (notification is Resumed) {
            applied += "before"
            delay(stalls.toMillis().milliseconds)
            applied += "after"
        }
    }
}

internal fun List<String>.endEvents() = count { it.startsWith("failed:") || it.startsWith("terminated:") }

internal fun InMemoryMetricReader.points(metric: String) =
    collectAllMetrics()
        .filter { it.name == metric }
        .flatMap { it.longSumData.points }

internal fun InMemoryMetricReader.outcomes(): Map<Pair<String?, String?>, Long> =
    points("saga.outcome").associate { point ->
        val outcome = point.attributes.get(AttributeKey.stringKey("saga.outcome"))
        val terminal = point.attributes.get(AttributeKey.stringKey("saga.terminal"))
        (outcome to terminal) to point.value
    }

internal fun InMemoryMetricReader.observerFailureSum(outcome: String? = null): Long =
    collectAllMetrics()
        .filter { it.name == "saga.observer.failure" }
        .flatMap { metric -> metric.longSumData.points }
        .filter { point ->
            outcome == null || point.attributes.get(AttributeKey.stringKey("saga.observer.outcome")) == outcome
        }
        .sumOf { it.value }

internal val Throwable.rootCause: Throwable get() = generateSequence(this) { it.cause }.last()
