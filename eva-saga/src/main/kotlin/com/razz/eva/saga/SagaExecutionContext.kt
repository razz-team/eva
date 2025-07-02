package com.razz.eva.saga

import io.opentelemetry.api.OpenTelemetry

data class SagaExecutionContext(
    val otel: OpenTelemetry,
)

fun defaultSagaExecutionContext(): SagaExecutionContext {
    return SagaExecutionContext(OpenTelemetry.noop())
}
