package com.razz.eva.saga

import io.opentelemetry.api.OpenTelemetry

data class SagaExecutionContext internal constructor(
    internal val otel: OpenTelemetry,
)

fun sagaExecutionContext(
    otel: OpenTelemetry = OpenTelemetry.noop(),
): SagaExecutionContext {
    return SagaExecutionContext(otel)
}
