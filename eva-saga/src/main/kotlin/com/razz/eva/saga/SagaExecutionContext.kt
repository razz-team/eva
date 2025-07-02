package com.razz.eva.saga

import io.opentelemetry.api.OpenTelemetry

data class SagaExecutionContext(
    internal val otel: OpenTelemetry,
)

fun defaultSagaExecutionContext(): SagaExecutionContext {
    return SagaExecutionContext(OpenTelemetry.noop())
}
