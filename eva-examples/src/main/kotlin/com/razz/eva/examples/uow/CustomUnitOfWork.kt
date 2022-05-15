package com.razz.eva.examples.uow

import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.BaseUnitOfWork.Configuration.Companion.default
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesWithoutResult
import com.razz.eva.domain.Principal
import com.razz.eva.uow.params.UowParams
import java.time.Clock

abstract class CustomUnitOfWork<PRINCIPAL, PARAMS, RESULT>(
    clock: Clock,
    configuration: Configuration = default()
) : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, CustomChangesDsl>(clock, configuration)
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    final override suspend fun changes(init: suspend CustomChangesDsl.() -> RESULT): Changes<RESULT> {
        return CustomChangesDsl.changes(ChangesWithoutResult(), init)
    }
}
