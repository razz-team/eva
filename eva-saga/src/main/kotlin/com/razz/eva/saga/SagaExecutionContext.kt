package com.razz.eva.saga

import com.razz.eva.saga.OtelAttributes.OBSERVER_OUTCOME
import com.razz.eva.saga.OtelAttributes.SAGA_ATTEMPT
import com.razz.eva.saga.OtelAttributes.SAGA_EXCEPTION
import com.razz.eva.saga.OtelAttributes.SAGA_NAME
import com.razz.eva.saga.OtelAttributes.NONE
import com.razz.eva.saga.OtelAttributes.SAGA_OUTCOME
import com.razz.eva.saga.OtelAttributes.SAGA_TERMINAL
import com.razz.eva.tracing.getEvaMeter
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongCounter
import java.time.Duration

private const val COUNT = "count"

data class SagaExecutionContext internal constructor(
    internal val otel: OpenTelemetry,
    internal val observerTimeout: Duration,
) {
    private val observerFailures: LongCounter = otel.getEvaMeter()
        .counterBuilder(Metrics.OBSERVER_FAILURE)
        .setDescription("Saga observer invocations that failed or timed out")
        .setUnit(COUNT)
        .build()

    private val restarts: LongCounter = otel.getEvaMeter()
        .counterBuilder(Metrics.RESTART)
        .setDescription("Saga runs restarted from init after onException declined to map a failure")
        .setUnit(COUNT)
        .build()

    private val outcomes: LongCounter = otel.getEvaMeter()
        .counterBuilder(Metrics.OUTCOME)
        .setDescription("Saga resumptions counted by how they ended")
        .setUnit(COUNT)
        .build()

    internal fun recordOutcome(sagaName: String, outcome: RunOutcome, terminal: String?) {
        outcomes.add(
            1,
            Attributes.of(
                SAGA_NAME,
                sagaName,
                SAGA_OUTCOME,
                outcome.value,
                SAGA_TERMINAL,
                terminal ?: NONE,
            ),
        )
    }

    internal fun recordObserverFailure(sagaName: String, outcome: ObserverOutcome) {
        observerFailures.add(
            1,
            Attributes.of(
                SAGA_NAME,
                sagaName,
                OBSERVER_OUTCOME,
                outcome.value,
            ),
        )
    }

    internal fun recordRestart(sagaName: String, attempt: Int, exception: String) {
        restarts.add(
            1,
            Attributes.of(
                SAGA_NAME,
                sagaName,
                SAGA_ATTEMPT,
                attempt.toLong(),
                SAGA_EXCEPTION,
                exception,
            ),
        )
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
