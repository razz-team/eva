package com.razz.eva.tracing

import io.opentelemetry.context.propagation.TextMapSetter

internal class StringMapSetter : TextMapSetter<MutableMap<String, String>> {
    override fun set(carrier: MutableMap<String, String>?, key: String, value: String) {
        carrier?.set(key, value)
    }
}
