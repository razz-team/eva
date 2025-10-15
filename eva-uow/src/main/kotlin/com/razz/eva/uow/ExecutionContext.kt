package com.razz.eva.uow

import io.opentelemetry.api.OpenTelemetry
import java.time.InstantSource

data class ExecutionContext internal constructor(
    internal val clock: InstantSource,
    internal val otel: OpenTelemetry,
) {
    @ExecutionContextApi
    fun withClock(clock: InstantSource) = copy(clock = clock)
}

@RequiresOptIn
annotation class ExecutionContextApi
