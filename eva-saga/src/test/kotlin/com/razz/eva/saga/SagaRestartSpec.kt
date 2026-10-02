package com.razz.eva.saga

import com.razz.eva.domain.Principal
import com.razz.eva.saga.TestSaga.Intermediary.Step0
import com.razz.eva.saga.TestSaga.Intermediary.Step1
import com.razz.eva.saga.TestSaga.Params
import com.razz.eva.saga.TestSaga.Terminal.Finish0
import com.razz.eva.saga.TestSaga.TestPrincipal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import java.time.Duration

internal class SagaRestartSpec : ShouldSpec({

    val principal = TestPrincipal(Principal.Id("cool-id"))

    should("refuse a negative restart backoff before notifying observers") {
        val observer = RecordingObserver()
        val failure = IllegalArgumentException("can't touch this")
        val params = Params(
            { throw failure },
            { _, _, _, _ -> null },
        )
        val rejection = shouldThrow<IllegalArgumentException> {
            TestSaga(restartPolicy = { _, _ -> Duration.ofMillis(-5) }).resume(principal, params)
        }
        rejection.message shouldBe "Saga restart backoff cannot be negative but was -5"
        rejection.rootCause shouldBe failure
        observer.events.shouldBeEmpty()
    }
    should("refuse a negative restart backoff that truncates to no delay") {
        val params = Params(
            { throw IllegalStateException("can't touch this") },
            { _, _, _, _ -> null },
        )

        shouldThrow<IllegalArgumentException> {
            TestSaga(restartPolicy = { _, _ -> Duration.ofNanos(-500_000) }).resume(principal, params)
        }
    }
    should("count a restart the policy asked to run with no delay") {
        val metricReader = InMemoryMetricReader.create()
        val spanExporter = InMemorySpanExporter.create()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
            .setTracerProvider(
                SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build(),
            )
            .build()
        var thrown = false
        val params = Params(
            { if (thrown) Finish0("stop") else { thrown = true; throw IllegalStateException("can't touch this") } },
            { _, _, _, _ -> null },
        )

        TestSaga(listOf(), sagaExecutionContext(otel = openTelemetry), null, { _, _ -> Duration.ZERO })
            .resume(principal, params) shouldBe Finish0("stop")

        metricReader.points("saga.restart").sumOf { it.value } shouldBe 1
        metricReader.outcomes() shouldBe mapOf(("terminal" to "Finish0") to 1L)
        spanExporter.finishedSpanItems.single { it.name == "TestSaga" }
            .events.map { it.name } shouldContain "saga.restart"
    }
    should("record no outcome when a restart backoff is rejected") {
        val metricReader = InMemoryMetricReader.create()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
            .build()
        val params = Params(
            { throw IllegalStateException("can't touch this") },
            { _, _, _, _ -> null },
        )

        shouldThrow<IllegalArgumentException> {
            TestSaga(
                listOf(),
                sagaExecutionContext(otel = openTelemetry),
                null,
                { _, _ -> Duration.ofMillis(-5) },
            ).resume(principal, params)
        }

        metricReader.outcomes() shouldBe mapOf()
        metricReader.points("saga.restart") shouldBe listOf()
    }
    should("still bound restarts when the policy asks for no delay") {
        val observer = RecordingObserver()
        val params = Params(
            { throw IllegalStateException("can't touch this") },
            { _, _, _, _ -> null },
        )

        shouldThrow<IllegalStateException> {
            TestSaga(listOf(observer), restartPolicy = { attempt, _ ->
                Duration.ZERO.takeIf { attempt < 2 }
            }).resume(principal, params)
        }

        observer.failureWillRestart shouldBe listOf(true, true, false)
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
                restartPolicy = { attempt, _ -> Duration.ofMillis(1).takeIf { attempt < 2 } },
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
                    Duration.ofMillis(1).takeIf { attempt < 1 }
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
    should("report a run time that spans every attempt and the backoff between them") {
        val observer = RecordingObserver()
        var attempts = 0
        val params = Params(
            {
                attempts++
                if (attempts > 2) Finish0("stop") else throw IllegalStateException("can't touch this")
            },
            { _, _, _, _ -> null },
        )

        TestSaga(listOf(observer), restartPolicy = { _, _ -> Duration.ofMillis(120) })
            .resume(principal, params) shouldBe Finish0("stop")

        val (attemptElapsed, runElapsed) = observer.terminalElapsed.single()
        runElapsed shouldBeGreaterThan attemptElapsed
        runElapsed shouldBeGreaterThan Duration.ofMillis(240)
        observer.failureRunElapsed.size shouldBe 2
        observer.failureRunElapsed[1] shouldBeGreaterThan observer.failureRunElapsed[0]
    }
})
