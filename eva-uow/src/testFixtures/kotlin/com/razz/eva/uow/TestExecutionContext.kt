package com.razz.eva.uow

import io.opentelemetry.api.OpenTelemetry
import java.time.Clock

object TestExecutionContext {

    fun executionContextForSpec(clock: Clock, otel: OpenTelemetry) =
        ExecutionContext(clock, otel)
}
