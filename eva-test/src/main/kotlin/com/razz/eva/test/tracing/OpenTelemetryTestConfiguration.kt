package com.razz.eva.test.tracing

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.MetricReader
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter

object OpenTelemetryTestConfiguration {
    fun create(
        spanExporter: SpanExporter = InMemorySpanExporter.create(),
        metricReader: MetricReader = InMemoryMetricReader.create(),
        logRecordExporter: LogRecordExporter = InMemoryLogRecordExporter.create(),
    ): OpenTelemetrySdk {
        val tracerProvider =
            SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build()

        val meterProvider =
            SdkMeterProvider.builder().registerMetricReader(metricReader).build()

        val loggerProvider =
            SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(logRecordExporter))
                .build()

        return OpenTelemetrySdk.builder()
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .build()
    }
}
