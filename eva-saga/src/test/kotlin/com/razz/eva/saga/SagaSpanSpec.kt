package com.razz.eva.saga

import com.razz.eva.domain.Principal
import com.razz.eva.saga.TestSaga.Intermediary.Step0
import com.razz.eva.saga.TestSaga.Intermediary.Step1
import com.razz.eva.saga.TestSaga.Params
import com.razz.eva.saga.TestSaga.Terminal.Finish0
import com.razz.eva.saga.TestSaga.Terminal.Finish1
import com.razz.eva.saga.TestSaga.TestPrincipal
import com.razz.eva.tracing.use
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode.ERROR
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor

internal class SagaSpanSpec : ShouldSpec({

    val principal = TestPrincipal(Principal.Id("cool-id"))

    fun tracedSaga(name: String? = null): Triple<InMemorySpanExporter, TestSaga, OpenTelemetrySdk> {
        val exporter = InMemorySpanExporter.create()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build(),
            )
            .build()
        val saga = TestSaga(listOf(), sagaExecutionContext(otel = openTelemetry), name)
        return Triple(exporter, saga, openTelemetry)
    }

    fun exporterAndSaga(name: String? = null): Pair<InMemorySpanExporter, TestSaga> {
        val (exporter, saga, _) = tracedSaga(name)
        return exporter to saga
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

    should("record the failure on the run span") {
        val (exporter, saga) = exporterAndSaga()
        val params = Params(
            { throw IllegalArgumentException("can't touch this") },
            { _, _, _, _ -> Finish1("swallowed") },
        )

        saga.resume(principal, params)

        val run = exporter.finishedSpanItems.single { it.name == "TestSaga" }
        run.status.statusCode shouldBe ERROR
        run.events.map { it.name } shouldContain "exception"
    }

    should("record the terminal the run ended on as an attribute of the run span") {
        val (exporter, saga) = exporterAndSaga()

        saga.resume(principal, Params({ Finish0("stop") }))

        val run = exporter.finishedSpanItems.single { it.name == "TestSaga" }
        run.attributes.get(AttributeKey.stringKey("saga.terminal")) shouldBe "Finish0"
    }

    should("start a fresh run span for a restarted run rather than nesting it under its predecessor") {
        val (exporter, saga) = exporterAndSaga()
        var wasThrown = false
        val params = Params(
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

        saga.resume(principal, params)

        val runs = exporter.finishedSpanItems.filter { it.name == "TestSaga" }
        runs.size shouldBe 2
        runs.none { run -> runs.any { it.spanId == run.parentSpanId } } shouldBe true
    }

    should("keep every run span, restarts included, a child of the caller's span") {
        val (exporter, saga, openTelemetry) = tracedSaga()
        var wasThrown = false
        val params = Params(
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
        val caller = openTelemetry.getTracer("test").spanBuilder("caller").startSpan()

        caller.use { saga.resume(principal, params) }

        val runs = exporter.finishedSpanItems.filter { it.name == "TestSaga" }
        runs.size shouldBe 2
        runs.map { it.parentSpanId }.distinct() shouldBe listOf(caller.spanContext.spanId)
    }

    should("carry the run id on every run span and the parent run id on a restart") {
        val (exporter, saga) = exporterAndSaga()
        var wasThrown = false
        val params = Params(
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

        saga.resume(principal, params)

        val runs = exporter.finishedSpanItems.filter { it.name == "TestSaga" }
        val runIds = runs.map { it.attributes.get(AttributeKey.stringKey("saga.run_id")) }
        runIds.filterNotNull().size shouldBe 2
        runs[0].attributes.get(AttributeKey.stringKey("saga.parent_run_id")) shouldBe null
        runs[1].attributes.get(AttributeKey.stringKey("saga.parent_run_id")) shouldBe runIds[0]
        runs.map { it.attributes.get(AttributeKey.longKey("saga.attempt")) } shouldBe listOf(0L, 1L)
    }

    should("name the run span after the delegate when the saga renames itself") {
        val (exporter, saga) = exporterAndSaga(name = "DelegateSaga")

        saga.resume(principal, Params({ Finish0("stop") }))

        exporter.finishedSpanItems.map { it.name } shouldContain "DelegateSaga"
    }
})
