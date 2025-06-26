package com.razz.eva.tracing

import io.opentelemetry.api.OpenTelemetry

fun OpenTelemetry.getEvaTracer() = getTracer("eva")

fun OpenTelemetry.getEvaMeter() = getMeter("eva")
