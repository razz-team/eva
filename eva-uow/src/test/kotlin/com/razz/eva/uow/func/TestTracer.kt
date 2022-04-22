package com.razz.eva.uow.func

import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.propagation.Format

class TestTracer(private val delegate: Tracer) : Tracer by delegate {

    val traceToSpan = mutableMapOf<String, String>()

    override fun <C> inject(spanContext: SpanContext, format: Format<C>, carrier: C) {
        traceToSpan[spanContext.toTraceId()] = spanContext.toSpanId()
        delegate.inject(spanContext, format, carrier)
    }
}
