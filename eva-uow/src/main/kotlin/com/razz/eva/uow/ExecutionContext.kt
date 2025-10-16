package com.razz.eva.uow

import io.opentelemetry.api.OpenTelemetry
import java.time.Clock
import java.time.InstantSource

data class ExecutionContext internal constructor(
    internal val clock: InstantSource,
    internal val otel: OpenTelemetry,
) {
    @ExecutionContextApi
    fun withClock(clock: Clock) = copy(clock = clock)
}

@RequiresOptIn
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExecutionContextApi
