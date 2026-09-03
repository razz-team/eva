package com.razz.eva.saga

import com.razz.eva.domain.Principal
import com.razz.eva.saga.Saga.SagaHaltException
import com.razz.eva.saga.SagaNotification.Failed
import com.razz.eva.saga.SagaNotification.Resumed
import com.razz.eva.saga.SagaNotification.Terminated
import com.razz.eva.saga.SagaNotification.Transitioned
import com.razz.eva.saga.TestSaga.Intermediary.Step0
import com.razz.eva.saga.TestSaga.Intermediary.Step1
import com.razz.eva.saga.TestSaga.Params
import com.razz.eva.saga.TestSaga.Terminal.Finish0
import com.razz.eva.saga.TestSaga.Terminal.Finish1
import com.razz.eva.saga.TestSaga.TestPrincipal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode.ERROR
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import java.time.Duration

internal class RecordingObserver : SagaObserver<TestPrincipal, Params> {

    val events = mutableListOf<String>()
    val runIds = mutableListOf<SagaRunId>()
    val parents = mutableListOf<Pair<SagaRunId, SagaRunId?>>()
    val sagaNames = mutableListOf<String>()

    override suspend fun onNotification(notification: SagaNotification<TestPrincipal, Params>) {
        val run = notification.run
        runIds += run.id
        events += when (notification) {
            is Resumed -> {
                parents += run.id to run.parentId
                sagaNames += run.sagaName
                "resumed:${notification.first::class.simpleName}"
            }
            is Transitioned ->
                "transition:${notification.from::class.simpleName}->${notification.to::class.simpleName}"
            is Terminated -> "terminated:${notification.terminal::class.simpleName}"
            is Failed -> {
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
        delay(takes.toMillis())
}

internal class TwoStepObserver(private val stalls: Duration) : SagaObserver<TestPrincipal, Params> {

    val applied = mutableListOf<String>()

    override suspend fun onNotification(notification: SagaNotification<TestPrincipal, Params>) {
        if (notification is Resumed) {
            applied += "before"
            delay(stalls.toMillis())
            applied += "after"
        }
    }
}

private fun List<String>.endEvents() = count { it.startsWith("failed:") || it.startsWith("terminated:") }

private fun InMemoryMetricReader.observerFailureSum(outcome: String? = null): Long =
    collectAllMetrics()
        .filter { it.name == "saga.observer.failure" }
        .flatMap { metric -> metric.longSumData.points }
        .filter { point ->
            outcome == null || point.attributes.get(AttributeKey.stringKey("saga.observer.outcome")) == outcome
        }
        .sumOf { it.value }

internal class SagaObserverSpec : ShouldSpec({

    val principal = TestPrincipal(Principal.Id("cool-id"))

    should("notify observer of resume, transition and terminal in order") {
        val observer = RecordingObserver()
        val params = Params({ step ->
            when (step) {
                is Step0 -> Step1("go go go!")
                else -> Finish0("it's time to stop")
            }
        })

        TestSaga(listOf(observer)).resume(principal, params)

        observer.events shouldBe listOf(
            "resumed:Step1",
            "transition:Step1->Finish0",
            "terminated:Finish0",
        )
    }

    should("share one run id across every event of a run") {
        val observer = RecordingObserver()
        val params = Params({ Finish0("it's time to stop") })

        TestSaga(listOf(observer)).resume(principal, params)

        observer.runIds.distinct().size shouldBe 1
    }

    should("notify observer of failure with the terminal the exception was mapped to") {
        val observer = RecordingObserver()
        val params = Params(
            { step ->
                when (step) {
                    is Step0 -> Step1("go go go!")
                    else -> throw IllegalArgumentException("can't touch this")
                }
            },
            { _, _, _, _ -> Finish1("swallowed") },
        )

        TestSaga(listOf(observer)).resume(principal, params)

        observer.events shouldBe listOf(
            "resumed:Step1",
            "failed:Step1:IllegalArgumentException:Finish1",
        )
    }

    should("end a run with exactly one failure when init threw and was mapped") {
        val observer = RecordingObserver()
        val params = Params(
            { throw IllegalArgumentException("can't touch this") },
            { _, _, _, _ -> Finish1("swallowed") },
        )

        TestSaga(listOf(observer)).resume(principal, params)

        observer.events shouldBe listOf("failed:null:IllegalArgumentException:Finish1")
    }

    should("notify observer of failure before an unmapped exception propagates") {
        val observer = RecordingObserver()
        val params = Params({ Step0("What does the \"B\" Stand for in \"Benoit B. Mandelbrot\"?") })

        shouldThrow<SagaHaltException> { TestSaga(listOf(observer)).resume(principal, params) }

        observer.events shouldBe listOf(
            "resumed:Step0",
            "failed:Step0:SagaHaltException:null",
        )
    }

    should("carry the previous run id as parent when onException restarts the saga") {
        val observer = RecordingObserver()
        var wasThrown = false
        val params = Params(
            { step ->
                when (step) {
                    is Step0 -> Step1("go go go!")
                    else -> if (wasThrown) {
                        Finish0("it's time to stop")
                    } else {
                        wasThrown = true
                        throw IllegalArgumentException("can't touch this")
                    }
                }
            },
            { _, _, _, _ -> null },
        )

        TestSaga(listOf(observer)).resume(principal, params)

        val runs = observer.parents.distinct()
        runs.size shouldBe 2
        runs[0].second shouldBe null
        runs[1].second shouldBe runs[0].first
    }

    should("keep the saga on course when an observer throws") {
        val observer = RecordingObserver()
        val params = Params({ Finish0("it's time to stop") })

        val broken = ThrowingObserver { IllegalStateException("observer is broken") }
        val state = TestSaga(listOf(broken, observer)).resume(principal, params)

        state shouldBe Finish0("it's time to stop")
        observer.events shouldBe listOf("resumed:Finish0", "terminated:Finish0")
    }

    should("let an Error from an observer abort the saga rather than swallow it") {
        val observer = RecordingObserver()
        val metricReader = InMemoryMetricReader.create()
        val spanExporter = InMemorySpanExporter.create()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
            .setTracerProvider(
                SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build(),
            )
            .build()
        val broken = ThrowingObserver { AssertionError("observer is not implemented") }

        shouldThrow<AssertionError> {
            TestSaga(
                listOf(broken, observer),
                sagaExecutionContext(otel = openTelemetry),
            ).resume(principal, Params({ Finish0("it's time to stop") }))
        }

        observer.events shouldBe listOf()
        metricReader.observerFailureSum() shouldBe 0
        val notifySpan = spanExporter.finishedSpanItems.single { it.name == "TestSaga-onResumed" }
        notifySpan.events.map { it.name } shouldContain "exception"
        notifySpan.status.statusCode shouldBe ERROR
    }

    should("keep the saga on course when an observer leaks a cancellation of its own") {
        val observer = RecordingObserver()
        val metricReader = InMemoryMetricReader.create()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
            .build()
        val leaking = ThrowingObserver { CancellationException("the observer's own child was cancelled") }

        val state = TestSaga(
            listOf(leaking, observer),
            sagaExecutionContext(otel = openTelemetry),
        ).resume(principal, Params({ Finish0("it's time to stop") }))

        state shouldBe Finish0("it's time to stop")
        observer.events shouldBe listOf("resumed:Finish0", "terminated:Finish0")
        metricReader.observerFailureSum(outcome = "threw") shouldBe 2
        metricReader.observerFailureSum(outcome = "timed_out") shouldBe 0
    }

    should("record exactly one failure for a run that fails two steps deep") {
        val observer = RecordingObserver()
        var call = 0
        val params = Params({
            when (call++) {
                0 -> Step0("first")
                1 -> Step1("second")
                else -> throw IllegalArgumentException("can't touch this")
            }
        })

        shouldThrow<IllegalArgumentException> { TestSaga(listOf(observer)).resume(principal, params) }

        observer.events shouldBe listOf(
            "resumed:Step0",
            "transition:Step0->Step1",
            "failed:Step1:IllegalArgumentException:null",
        )
    }

    should("end a halted run three steps deep with exactly one failure") {
        val observer = RecordingObserver()
        var call = 0
        val params = Params({
            when (call++) {
                0 -> Step0("first")
                1 -> Step1("second")
                else -> Step0("seen this one already")
            }
        })

        shouldThrow<SagaHaltException> { TestSaga(listOf(observer)).resume(principal, params) }

        observer.events.endEvents() shouldBe 1
        observer.events shouldBe listOf(
            "resumed:Step0",
            "transition:Step0->Step1",
            "failed:Step1:SagaHaltException:null",
        )
    }

    should("end a mapped failure two steps deep with exactly one failure") {
        val observer = RecordingObserver()
        var call = 0
        val params = Params(
            {
                when (call++) {
                    0 -> Step0("first")
                    1 -> Step1("second")
                    else -> throw IllegalArgumentException("can't touch this")
                }
            },
            { _, _, _, _ -> Finish1("swallowed") },
        )

        TestSaga(listOf(observer)).resume(principal, params) shouldBe Finish1("swallowed")

        observer.events shouldBe listOf(
            "resumed:Step0",
            "transition:Step0->Step1",
            "failed:Step1:IllegalArgumentException:Finish1",
        )
    }

    should("count every observer invocation that threw") {
        val metricReader = InMemoryMetricReader.create()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
            .build()
        val params = Params({ Finish0("it's time to stop") })

        TestSaga(
            listOf(ThrowingObserver { IllegalStateException("observer is broken") }),
            sagaExecutionContext(otel = openTelemetry),
        ).resume(principal, params)

        metricReader.observerFailureSum() shouldBe 2
    }

    should("abandon an observer that outruns the timeout and carry on with the rest") {
        val observer = RecordingObserver()
        val metricReader = InMemoryMetricReader.create()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
            .build()
        val context = sagaExecutionContext(
            otel = openTelemetry,
            observerTimeout = Duration.ofMillis(20),
        )

        val startedAt = System.nanoTime()
        val state = TestSaga(
            listOf(SlowObserver(Duration.ofSeconds(30)), observer),
            context,
        ).resume(principal, Params({ Finish0("it's time to stop") }))
        val elapsed = Duration.ofNanos(System.nanoTime() - startedAt)

        state shouldBe Finish0("it's time to stop")
        observer.events shouldBe listOf("resumed:Finish0", "terminated:Finish0")
        metricReader.observerFailureSum(outcome = "timed_out") shouldBe 2
        metricReader.observerFailureSum(outcome = "threw") shouldBe 0
        elapsed.seconds shouldBeLessThan 5
    }

    should("bound an observer notification to three seconds unless told otherwise") {
        sagaExecutionContext().observerTimeout shouldBe Duration.ofSeconds(3)
    }

    should("stop restarting once the policy declines and rethrow the failure that caused it") {
        val observer = RecordingObserver()
        val params = Params(
            { throw IllegalArgumentException("can't touch this") },
            { _, _, _, _ -> null },
        )

        shouldThrow<IllegalArgumentException> {
            TestSaga(
                listOf(observer),
                restartPolicy = { attempt, _ -> Duration.ZERO.takeIf { attempt < 2 } },
            ).resume(principal, params)
        }

        observer.runIds.distinct().size shouldBe 3
        val perRun = observer.runIds.zip(observer.events).groupBy({ it.first }, { it.second })
        perRun.values.map { it.endEvents() } shouldBe listOf(1, 1, 1)
    }

    should("not restart at all when the policy declines the first attempt") {
        val observer = RecordingObserver()
        val params = Params(
            { throw IllegalArgumentException("can't touch this") },
            { _, _, _, _ -> null },
        )

        shouldThrow<IllegalArgumentException> {
            TestSaga(listOf(observer), restartPolicy = { _, _ -> null }).resume(principal, params)
        }

        observer.runIds.distinct().size shouldBe 1
    }

    should("hand the policy the attempt number and the failure that triggered the restart") {
        val seen = mutableListOf<Pair<Int, String>>()
        val params = Params(
            { throw IllegalArgumentException("can't touch this") },
            { _, _, _, _ -> null },
        )

        shouldThrow<IllegalArgumentException> {
            TestSaga(
                restartPolicy = { attempt, ex ->
                    seen += attempt to (ex::class.simpleName ?: "")
                    Duration.ZERO.takeIf { attempt < 1 }
                },
            ).resume(principal, params)
        }

        seen shouldBe listOf(0 to "IllegalArgumentException", 1 to "IllegalArgumentException")
    }

    should("restart once by default so an existing null-returning onException keeps working") {
        val observer = RecordingObserver()
        var wasThrown = false
        val params = Params(
            { step ->
                when (step) {
                    is Step0 -> Step1("go go go!")
                    else -> if (wasThrown) {
                        Finish0("it's time to stop")
                    } else {
                        wasThrown = true
                        throw IllegalArgumentException("can't touch this")
                    }
                }
            },
            { _, _, _, _ -> null },
        )

        TestSaga(listOf(observer)).resume(principal, params) shouldBe Finish0("it's time to stop")

        observer.runIds.distinct().size shouldBe 2
    }

    should("abandon a timed out observer mid-flight, leaving whatever it had already done in place") {
        val torn = TwoStepObserver(Duration.ofSeconds(30))
        val context = sagaExecutionContext(observerTimeout = Duration.ofMillis(20))

        val state = TestSaga(listOf(torn), context)
            .resume(principal, Params({ Finish0("it's time to stop") }))

        state shouldBe Finish0("it's time to stop")
        torn.applied shouldBe listOf("before")
    }

    should("refuse an observer timeout that is not positive") {
        shouldThrow<IllegalArgumentException> { sagaExecutionContext(observerTimeout = Duration.ZERO) }
        shouldThrow<IllegalArgumentException> { sagaExecutionContext(observerTimeout = Duration.ofSeconds(-1)) }
    }

    should("refuse an observer timeout that rounds down to no timeout at all") {
        shouldThrow<IllegalArgumentException> { sagaExecutionContext(observerTimeout = Duration.ofNanos(500)) }
    }

    should("report the concrete saga class as the run's name") {
        val observer = RecordingObserver()

        TestSaga(listOf(observer)).resume(principal, Params({ Finish0("stop") }))

        observer.sagaNames shouldBe listOf("TestSaga")
    }

    should("let a decorating saga report the delegate's name instead of its own") {
        val observer = RecordingObserver()

        TestSaga(listOf(observer), name = "DelegateSaga").resume(principal, Params({ Finish0("stop") }))

        observer.sagaNames shouldBe listOf("DelegateSaga")
    }
})
