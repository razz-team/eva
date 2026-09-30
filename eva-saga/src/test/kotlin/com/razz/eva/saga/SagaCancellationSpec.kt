package com.razz.eva.saga

import com.razz.eva.domain.Principal
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.opentelemetry.api.trace.StatusCode.ERROR
import io.opentelemetry.api.trace.StatusCode.UNSET
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import com.razz.eva.saga.TestSaga.Intermediary.Step0
import com.razz.eva.saga.TestSaga.Intermediary.Step1
import com.razz.eva.saga.TestSaga.Terminal.Finish0
import com.razz.eva.saga.TestSaga.Terminal.Finish1
import com.razz.eva.saga.TestSaga.Params
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

internal class SagaCancellationSpec : ShouldSpec({
    val principal = TestSaga.TestPrincipal(Principal.Id("saga-cancellation-spec-principal-id"))

    fun initHelpers(
        observer: RecordingObserver,
        restartPolicy: ((Int, Exception) -> Duration?)? = null,
    ): Triple<TestSaga, InMemoryMetricReader, InMemorySpanExporter> {
        val metricReader = InMemoryMetricReader.create()
        val spanExporter = InMemorySpanExporter.create()
        val meterProvider = SdkMeterProvider
            .builder()
            .registerMetricReader(metricReader)
            .build()
        val traceProvider = SdkTracerProvider
            .builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setMeterProvider(meterProvider)
            .setTracerProvider(traceProvider)
            .build()
        val testSaga = TestSaga(
            observers = listOf(observer),
            executionContext = sagaExecutionContext(otel = openTelemetry),
            name = null,
            restartPolicy = restartPolicy,
        )
        return Triple(
            testSaga,
            metricReader,
            spanExporter,
        )
    }

    suspend fun cancelOnceReached(
        saga: TestSaga,
        params: Params,
        reached: CompletableDeferred<Unit>
    ): Throwable? {
        var thrown: Throwable? = null
        val scope = CoroutineScope(Dispatchers.Default)
        val running = scope.launch {
            try {
                saga.resume(principal, params)
            } catch (ex: Throwable) {
                thrown = ex
            }
        }
        reached.await()
        running.cancelAndJoin()
        scope.cancel()
        return thrown
    }

    should("propagate a caller's cancellation out of init instead of treating it as a step failure") {
        val observer = RecordingObserver()
        val (saga, metricReader, spanExporter) = initHelpers(observer)
        val reachedInit = CompletableDeferred<Unit>()
        val thrown = cancelOnceReached(
            saga,
            Params({
                reachedInit.complete(Unit)
                awaitCancellation()
            }),
            reachedInit,
        )
        thrown.shouldBeInstanceOf<CancellationException>()
        observer.events.shouldBeEmpty()
        metricReader.outcomes().shouldBeEmpty()
        metricReader.points("saga.restart").shouldBeEmpty()
        spanExporter.finishedSpanItems.map { it.name to it.status.statusCode } shouldBe listOf(
            "TestSaga-init" to ERROR,
            "TestSaga" to ERROR,
        )
    }

    should("propagate a caller's cancellation out of next instead of treating it as a step failure") {
        val observer = RecordingObserver()
        val (saga, metricReader, spanExporter) = initHelpers(observer)
        val reachedNext = CompletableDeferred<Unit>()
        val thrown = cancelOnceReached(
            saga,
            Params({ step ->
                when (step) {
                    is Step0 -> Step1("proceed")
                    is Step1 -> {
                        reachedNext.complete(Unit)
                        awaitCancellation()
                    }
                }
            }),
            reachedNext,
        )
        thrown.shouldBeInstanceOf<CancellationException>()
        observer.events shouldBe listOf("resumed:Step1")
        metricReader.outcomes().shouldBeEmpty()
        metricReader.points("saga.restart").shouldBeEmpty()
        spanExporter.finishedSpanItems.map { it.name to it.status.statusCode } shouldBe listOf(
            "TestSaga-init" to UNSET,
            "TestSaga-onResumed" to UNSET,
            "Step1-intermediate" to ERROR,
            "TestSaga" to ERROR,
        )
    }

    should("handle a terminal-mapped timeout cancellation from init") {
        val observer = RecordingObserver()
        val (saga, metricReader, spanExporter) = initHelpers(observer)
        val terminal = saga.resume(
            principal,
            Params(
                { withTimeout(50.milliseconds) { awaitCancellation() } },
                { _, _, _, _ -> Finish1("mapped") }
            )
        )
        terminal shouldBe Finish1("mapped")
        observer.events shouldBe listOf("failed:null:TimeoutCancellationException:Finish1")
        metricReader.outcomes() shouldBe mapOf(Pair("mapped", "Finish1") to 1)
        metricReader.points("saga.restart").shouldBeEmpty()
        spanExporter.finishedSpanItems.map { it.name } shouldBe listOf(
            "TestSaga-init",
            "TestSaga-onFailed",
            "TestSaga"
        )
    }

    should("handle a null-mapped (restart) timeout cancellation from next") {
        val observer = RecordingObserver()
        val (saga, metricReader, spanExporter) = initHelpers(observer) { _, _ -> Duration.ofMillis(1) }
        var timedOut = false
        val terminal = saga.resume(
            principal,
            Params(
                { step ->
                    when (step) {
                        is Step0 -> Step1("proceed")
                        is Step1 -> if (timedOut) {
                            Finish0("finished")
                        } else {
                            timedOut = true
                            withTimeout(50.milliseconds) { awaitCancellation() }
                        }
                    }
                },
                { _, _, _, _ -> null },
            )
        )
        terminal shouldBe Finish0("finished")
        observer.events shouldBe listOf(
            "resumed:Step1",
            "failed:Step1:TimeoutCancellationException:null",
            "resumed:Step1",
            "transition:Step1->Finish0",
            "terminated:Finish0",
        )
        metricReader.outcomes() shouldBe mapOf(Pair("terminal", "Finish0") to 1)
        metricReader.points("saga.restart") shouldHaveSize 1
        spanExporter.finishedSpanItems.map { it.name } shouldBe listOf(
            "TestSaga-init",
            "TestSaga-onResumed",
            "Step1-intermediate",
            "TestSaga-onFailed",
            "TestSaga-init",
            "TestSaga-onResumed",
            "Step1-intermediate",
            "TestSaga-onTransition",
            "TestSaga-onTerminated",
            "TestSaga"
        )
    }
})