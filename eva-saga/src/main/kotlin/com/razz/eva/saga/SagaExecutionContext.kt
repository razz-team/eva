package com.razz.eva.saga

import com.razz.eva.saga.OtelAttributes.OBSERVER_OUTCOME
import com.razz.eva.saga.OtelAttributes.SAGA_NAME
import com.razz.eva.tracing.getEvaMeter
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongCounter
import java.time.Duration

data class SagaExecutionContext internal constructor(
    internal val otel: OpenTelemetry,
    internal val observerTimeout: Duration,
) {
    private val observerFailures: LongCounter = otel.getEvaMeter()
        .counterBuilder(Metrics.OBSERVER_FAILURE)
        .setDescription("Saga observer invocations that failed or timed out")
        .setUnit("count")
        .build()

    private val restarts: LongCounter = otel.getEvaMeter()
        .counterBuilder(Metrics.RESTART)
        .setDescription("Saga runs restarted from init after onException declined to map a failure")
        .setUnit("count")
        .build()

    internal fun recordObserverFailure(sagaName: String, outcome: ObserverOutcome) {
        observerFailures.add(1, Attributes.of(SAGA_NAME, sagaName, OBSERVER_OUTCOME, outcome.value))
    }

    internal fun recordRestart(sagaName: String) {
        restarts.add(1, Attributes.of(SAGA_NAME, sagaName))
    }
}

fun sagaExecutionContext(
    otel: OpenTelemetry = OpenTelemetry.noop(),
    observerTimeout: Duration = Duration.ofSeconds(3),
): SagaExecutionContext {
    require(observerTimeout.toMillis() > 0) {
        "Saga observer timeout must be at least a millisecond, but was [$observerTimeout]"
    }
    return SagaExecutionContext(otel, observerTimeout)
}
