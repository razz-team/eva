package com.razz.eva.uow

import com.razz.eva.domain.Principal

abstract class UnitOfWork<PRINCIPAL, PARAMS, RESULT>(
    executionContext: ExecutionContext,
    configuration: Configuration = Configuration.default()
) : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, ChangesDsl>(executionContext, configuration)
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    final override suspend fun changes(init: suspend ChangesDsl.() -> RESULT): Changes<RESULT> {
        return ChangesDsl.changes(ChangesAccumulator(), init)
    }
}
