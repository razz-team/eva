package com.razz.eva.uow

import io.opentelemetry.api.OpenTelemetry
import java.time.Clock

data class ExecutionContext(
    val clock: Clock,
    val otel: OpenTelemetry,
)
