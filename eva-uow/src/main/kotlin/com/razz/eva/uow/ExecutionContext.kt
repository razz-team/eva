package com.razz.eva.uow

import io.opentelemetry.api.OpenTelemetry
import java.time.InstantSource

data class ExecutionContext internal constructor(
    internal val clock: InstantSource,
    internal val otel: OpenTelemetry,
) {
    fun withClock(clock: InstantSource) = copy(clock = clock)
}
