package com.razz.eva.uow

import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.propagation.Format

class TestTracer(private val delegate: Tracer): Tracer by delegate {

    var lastSpanId: String? = null

    override fun <C> inject(spanContext: SpanContext, format: Format<C>, carrier: C) {
        lastSpanId = spanContext.toSpanId()
        delegate.inject(spanContext, format, carrier)
    }
}