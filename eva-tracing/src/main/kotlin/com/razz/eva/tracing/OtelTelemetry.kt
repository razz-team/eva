package com.razz.eva.tracing

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer

fun OpenTelemetry.getEvaTracer(): Tracer = getTracer("eva")

fun OpenTelemetry.getEvaMeter(): Meter = getMeter("eva")

suspend fun <T> OpenTelemetry?.withSpan(
    spanName: String,
    parameters: (SpanBuilder.() -> Unit)? = null,
    block: suspend () -> T,
): T {
    if (this == null) {
        return block()
    } else {
        val span = startSpan(
            spanName = spanName,
            parameters = parameters,
        )
        return span.use {
            block()
        }
    }
}

fun OpenTelemetry.startSpan(spanName: String, parameters: (SpanBuilder.() -> Unit)? = null): Span {
    val tracer = getEvaTracer()
    return tracer.spanBuilder(spanName).run {
        if (parameters != null) parameters()
        startSpan()
    }
}
