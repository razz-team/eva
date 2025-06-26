package com.razz.eva.uow

import com.razz.eva.domain.Principal
import io.opentelemetry.api.OpenTelemetry
import java.time.Clock

abstract class UnitOfWork<PRINCIPAL, PARAMS, RESULT>(
    clock: Clock,
    configuration: Configuration = Configuration.default()
) : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, ChangesDsl>(ExecutionContext(clock, OpenTelemetry.noop()), configuration)
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    final override suspend fun changes(init: suspend ChangesDsl.() -> RESULT): Changes<RESULT> {
        return ChangesDsl.changes(ChangesAccumulator(), init)
    }
}
