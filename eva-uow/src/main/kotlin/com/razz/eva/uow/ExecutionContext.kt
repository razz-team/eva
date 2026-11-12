package com.razz.eva.uow

import io.opentelemetry.api.OpenTelemetry
import java.time.InstantSource

data class ExecutionContext internal constructor(
    internal val clock: InstantSource,
    internal val otel: OpenTelemetry,
    internal val inheritedChanges: ChangesAccumulator.HandOff? = null,
) {
    @ExecutionContextApi
    fun withClock(clock: InstantSource) = copy(clock = clock)

    internal fun withInheritedChanges(handOff: ChangesAccumulator.HandOff) = copy(inheritedChanges = handOff)
}

@RequiresOptIn
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExecutionContextApi
