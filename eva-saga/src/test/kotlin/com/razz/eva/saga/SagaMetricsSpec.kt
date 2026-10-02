package com.razz.eva.saga

import com.razz.eva.domain.Principal
import com.razz.eva.saga.TestSaga.Intermediary.Step0
import com.razz.eva.saga.TestSaga.Intermediary.Step1
import com.razz.eva.saga.TestSaga.Params
import com.razz.eva.saga.TestSaga.Terminal.Finish0
import com.razz.eva.saga.TestSaga.Terminal.Finish1
import com.razz.eva.saga.TestSaga.TestPrincipal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader

internal class SagaMetricsSpec : ShouldSpec({

    val principal = TestPrincipal(Principal.Id("cool-id"))

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
    should("count every resumption by how it ended") {
        val metricReader = InMemoryMetricReader.create()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
            .build()
        val context = sagaExecutionContext(otel = openTelemetry)
        val boom = IllegalStateException("can't touch this")

        TestSaga(executionContext = context)
            .resume(principal, Params({ Finish0("it's time to stop") }))
        TestSaga(executionContext = context)
            .resume(principal, Params({ throw boom }, { _, _, _, _ -> Finish1("swallowed") }))
        shouldThrow<IllegalStateException> {
            TestSaga(executionContext = context).resume(principal, Params({ throw boom }))
        }
        shouldThrow<IllegalStateException> {
            TestSaga(executionContext = context, restartPolicy = { _, _ -> null })
                .resume(principal, Params({ throw boom }, { _, _, _, _ -> null }))
        }

        metricReader.outcomes() shouldBe mapOf(
            ("terminal" to "Finish0") to 1L,
            ("mapped" to "Finish1") to 1L,
            ("rethrew" to "none") to 1L,
            ("gave_up" to "none") to 1L,
        )
    }
    should("attribute a restart to its attempt and the exception that caused it") {
        val metricReader = InMemoryMetricReader.create()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(metricReader).build())
            .build()
        var wasThrown = false
        val params = Params(
            { step ->
                when {
                    step is Step0 -> Step1("go go go!")
                    wasThrown -> Finish0("it's time to stop")
                    else -> {
                        wasThrown = true
                        throw IllegalStateException("can't touch this")
                    }
                }
            },
            { _, _, _, _ -> null },
        )

        TestSaga(executionContext = sagaExecutionContext(otel = openTelemetry))
            .resume(principal, params) shouldBe Finish0("it's time to stop")

        val restart = metricReader.points("saga.restart").single()
        restart.value shouldBe 1L
        restart.attributes.get(AttributeKey.stringKey("saga.name")) shouldBe "TestSaga"
        restart.attributes.get(AttributeKey.longKey("saga.attempt")) shouldBe 1L
        restart.attributes.get(AttributeKey.stringKey("saga.exception")) shouldBe "IllegalStateException"
    }
})
