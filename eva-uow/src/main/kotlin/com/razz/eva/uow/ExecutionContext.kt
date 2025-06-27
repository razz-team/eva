package com.razz.eva.uow

import io.opentelemetry.api.OpenTelemetry
import java.time.Clock

data class ExecutionContext internal constructor(
    internal val clock: Clock,
    internal val otel: OpenTelemetry,
)
