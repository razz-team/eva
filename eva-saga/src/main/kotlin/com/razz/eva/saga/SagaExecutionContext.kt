package com.razz.eva.saga

import io.opentelemetry.api.OpenTelemetry

data class SagaExecutionContext internal constructor(
    internal val otel: OpenTelemetry,
    internal val observers: List<SagaObserver>,
)

fun sagaExecutionContext(
    otel: OpenTelemetry = OpenTelemetry.noop(),
    observers: List<SagaObserver> = emptyList(),
): SagaExecutionContext {
    return SagaExecutionContext(otel, emptyList())
}
