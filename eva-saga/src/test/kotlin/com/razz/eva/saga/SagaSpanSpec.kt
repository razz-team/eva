package com.razz.eva.saga

import com.razz.eva.domain.Principal
import com.razz.eva.saga.TestSaga.Intermediary.Step0
import com.razz.eva.saga.TestSaga.Intermediary.Step1
import com.razz.eva.saga.TestSaga.Params
import com.razz.eva.saga.TestSaga.Terminal.Finish0
import com.razz.eva.saga.TestSaga.Terminal.Finish1
import com.razz.eva.saga.TestSaga.TestPrincipal
import com.razz.eva.tracing.use
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode.ERROR
import io.opentelemetry.api.trace.StatusCode.UNSET
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import java.time.Duration

internal class SagaSpanSpec : ShouldSpec({

    val principal = TestPrincipal(Principal.Id("cool-id"))

    fun tracedSaga(
        name: String? = null,
        restartPolicy: ((Int, Exception) -> Duration?)? = null,
    ): Triple<InMemorySpanExporter, TestSaga, OpenTelemetrySdk> {
        val exporter = InMemorySpanExporter.create()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build(),
            )
            .build()
        val saga = TestSaga(listOf(), sagaExecutionContext(otel = openTelemetry), name, restartPolicy)
        return Triple(exporter, saga, openTelemetry)
    }

    fun exporterAndSaga(name: String? = null): Pair<InMemorySpanExporter, TestSaga> {
        val (exporter, saga, _) = tracedSaga(name)
        return exporter to saga
    }

    fun restartingParams(): Params {
        var wasThrown = false
        return Params(
            { step ->
                when (step) {
                    is Step0 -> Step1("go go go!")
                    else -> if (wasThrown) {
                        Finish0("stop")
                    } else {
                        wasThrown = true
                        throw IllegalArgumentException("can't touch this")
                    }
                }
            },
            { _, _, _, _ -> null },
        )
    }

    should("span each notification so observer time is visible in the trace") {
        val exporter = InMemorySpanExporter.create()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build(),
            )
            .build()
        val saga = TestSaga(listOf(RecordingObserver()), sagaExecutionContext(otel = openTelemetry))

        saga.resume(principal, Params({ Finish0("stop") }))

        exporter.finishedSpanItems.map { it.name } shouldBe listOf(
            "TestSaga-init",
            "TestSaga-onResumed",
            "TestSaga-onTerminated",
            "TestSaga",
        )
    }

    should("open a span for the run and for init, and none for the terminal it ended on") {
        val (exporter, saga) = exporterAndSaga()

        saga.resume(principal, Params({ Finish0("stop") }))

        exporter.finishedSpanItems.map { it.name } shouldBe listOf("TestSaga-init", "TestSaga")
    }

    should("parent every step span under the run span") {
        val (exporter, saga) = exporterAndSaga()
        val params = Params({ step ->
            when (step) {
                is Step0 -> Step1("go go go!")
                else -> Finish0("stop")
            }
        })

        saga.resume(principal, params)

        val spans = exporter.finishedSpanItems
        val run = spans.single { it.name == "TestSaga" }
        val children = spans.filter { it.name != "TestSaga" }
        children.map { it.parentSpanId }.distinct() shouldBe listOf(run.spanId)
    }

    should("record a mapped failure on the run span without marking the run failed") {
        val (exporter, saga) = exporterAndSaga()
        val params = Params(
            { throw IllegalArgumentException("can't touch this") },
            { _, _, _, _ -> Finish1("swallowed") },
        )

        saga.resume(principal, params)

        val run = exporter.finishedSpanItems.single { it.name == "TestSaga" }
        run.events.map { it.name } shouldContain "exception"
        run.status.statusCode shouldBe UNSET
        run.attributes.get(AttributeKey.stringKey("saga.terminal")) shouldBe "Finish1"
    }

    should("mark the run failed when the saga gives up") {
        val (exporter, saga, _) = tracedSaga(restartPolicy = { _, _ -> null })
        val params = Params(
            { throw IllegalArgumentException("can't touch this") },
            { _, _, _, _ -> null },
        )

        shouldThrow<IllegalArgumentException> { saga.resume(principal, params) }

        val run = exporter.finishedSpanItems.single { it.name == "TestSaga" }
        run.status.statusCode shouldBe ERROR
        run.events.map { it.name } shouldContain "exception"
        run.attributes.get(AttributeKey.longKey("saga.attempts")) shouldBe 1L
    }

    should("record the terminal the run ended on as an attribute of the run span") {
        val (exporter, saga) = exporterAndSaga()

        saga.resume(principal, Params({ Finish0("stop") }))

        val run = exporter.finishedSpanItems.single { it.name == "TestSaga" }
        run.attributes.get(AttributeKey.stringKey("saga.terminal")) shouldBe "Finish0"
    }

    should("keep one run span for the whole resumption, restarts included") {
        val (exporter, saga) = exporterAndSaga()

        saga.resume(principal, restartingParams())

        val runs = exporter.finishedSpanItems.filter { it.name == "TestSaga" }
        runs.size shouldBe 1
        runs.single().attributes.get(AttributeKey.longKey("saga.attempts")) shouldBe 2L
        runs.single().attributes.get(AttributeKey.stringKey("saga.terminal")) shouldBe "Finish0"
    }

    should("keep the run span a child of the caller's span") {
        val (exporter, saga, openTelemetry) = tracedSaga()
        val caller = openTelemetry.getTracer("test").spanBuilder("caller").startSpan()

        caller.use { saga.resume(principal, restartingParams()) }

        val runs = exporter.finishedSpanItems.filter { it.name == "TestSaga" }
        runs.size shouldBe 1
        runs.single().parentSpanId shouldBe caller.spanContext.spanId
    }

    should("record each restart as an event chaining the new run id to its predecessor") {
        val (exporter, saga) = exporterAndSaga()

        saga.resume(principal, restartingParams())

        val run = exporter.finishedSpanItems.single { it.name == "TestSaga" }
        val firstRunId = run.attributes.get(AttributeKey.stringKey("saga.run_id"))
        firstRunId shouldNotBe null
        val restarts = run.events.filter { it.name == "saga.restart" }
        restarts.size shouldBe 1
        restarts.single().attributes.get(AttributeKey.stringKey("saga.parent_run_id")) shouldBe firstRunId
        restarts.single().attributes.get(AttributeKey.stringKey("saga.run_id")) shouldNotBe firstRunId
        restarts.single().attributes.get(AttributeKey.longKey("saga.attempt")) shouldBe 1L
    }

    should("hold the name captured at resume when the saga renames itself across a restart") {
        val observer = RecordingObserver()
        val exporter = InMemorySpanExporter.create()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build(),
            )
            .build()
        val saga = TestSaga(listOf(observer), sagaExecutionContext(otel = openTelemetry), "FirstName")
        var wasThrown = false
        val params = Params(
            { step ->
                when {
                    step is Step0 -> Step1("go go go!")
                    wasThrown -> Finish0("stop")
                    else -> {
                        wasThrown = true
                        saga.rename("SecondName")
                        throw IllegalArgumentException("can't touch this")
                    }
                }
            },
            { _, _, _, _ -> null },
        )

        saga.resume(principal, params)

        exporter.finishedSpanItems.filter { it.name.startsWith("SecondName") } shouldBe listOf()
        exporter.finishedSpanItems.single { it.name == "FirstName" }
            .attributes.get(AttributeKey.longKey("saga.attempts")) shouldBe 2L
        observer.sagaNames shouldBe listOf("FirstName", "FirstName")
    }

    should("name the run span after the delegate when the saga renames itself") {
        val (exporter, saga) = exporterAndSaga(name = "DelegateSaga")

        saga.resume(principal, Params({ Finish0("stop") }))

        exporter.finishedSpanItems.map { it.name } shouldBe listOf("DelegateSaga-init", "DelegateSaga")
    }
})
