package com.razz.eva.uow

import io.opentelemetry.api.OpenTelemetry
import java.time.InstantSource

/**
 * What a unit of work gets when it is built: the clock and the telemetry it runs with. The executor builds a new one
 * for each attempt, and it carries that attempt's [InstantiationContext]: a guarded unit of work reads only the
 * `Proven` fields of params built in that attempt, while the attempt runs. A context the executor did not build for an
 * attempt, and the context a composed child gets, carry no attempt.
 */
data class ExecutionContext internal constructor(
    internal val clock: InstantSource,
    internal val otel: OpenTelemetry,
    internal val inheritedChanges: ChangesAccumulator? = null,
    internal val attempt: InstantiationContext? = null,
) {
    @ExecutionContextApi
    fun withClock(clock: InstantSource) = copy(clock = clock)

    // A child takes no proof, so its context carries no attempt.
    internal fun withInheritedChanges(changes: ChangesAccumulator) = copy(inheritedChanges = changes, attempt = null)
}

@RequiresOptIn
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExecutionContextApi
