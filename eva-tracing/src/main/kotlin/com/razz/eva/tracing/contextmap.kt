package com.razz.eva.tracing

import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapPropagator

fun contextMap(propagator: TextMapPropagator): Map<String, String> {
    return mutableMapOf<String, String>().apply {
        propagator.inject(Context.current(), this, StringMapSetter())
    }
}
