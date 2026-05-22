package com.razz.eva.tracing

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer

fun OpenTelemetry.getEvaTracer(): Tracer = getTracer("eva")

fun OpenTelemetry.getEvaMeter(): Meter = getMeter("eva")

suspend fun <T> OpenTelemetry?.withSpan(
    spanName: String,
    parameters: (SpanBuilder.() -> Unit)? = null,
    block: suspend () -> T,
): T {
    val evaTracer = this?.getEvaTracer() ?: return block()

    val span = evaTracer
        .spanBuilder(spanName)
        .apply { parameters?.invoke(this) }
        .startSpan()

    return span.use {
        block()
    }
}
