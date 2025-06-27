package com.razz.eva.examples.changes.uow

import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.BaseUnitOfWork.Configuration.Companion.default
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.domain.Principal
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.UowParams

abstract class CustomUnitOfWork<PRINCIPAL, PARAMS, RESULT>(
    executionContext: ExecutionContext,
    configuration: Configuration = default()
) : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, CustomChangesDsl>(executionContext, configuration)
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    final override suspend fun changes(init: suspend CustomChangesDsl.() -> RESULT): Changes<RESULT> {
        return CustomChangesDsl.changes(ChangesAccumulator(), init)
    }
}
