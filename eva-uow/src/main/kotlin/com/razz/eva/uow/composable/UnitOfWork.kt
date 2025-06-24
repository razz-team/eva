package com.razz.eva.uow.composable

import com.razz.eva.domain.Principal
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.uow.UowParams
import io.opentelemetry.api.OpenTelemetry
import java.time.Clock

abstract class UnitOfWork<PRINCIPAL, PARAMS, RESULT>(
    clock: Clock,
    configuration: Configuration = Configuration.default(),
    private val otel: OpenTelemetry = OpenTelemetry.noop(),
) : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, ChangesDsl>(clock, configuration)
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    final override suspend fun changes(init: suspend ChangesDsl.() -> RESULT): Changes<RESULT> {
        return ChangesDsl.changes(ChangesAccumulator(), otel, init)
    }
}
