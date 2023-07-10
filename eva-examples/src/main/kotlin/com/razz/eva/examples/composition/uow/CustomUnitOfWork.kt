package com.razz.eva.examples.composition.uow

import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.BaseUnitOfWork.Configuration.Companion.default
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.domain.Principal
import com.razz.eva.uow.UowParams
import java.time.Clock

abstract class CustomUnitOfWork<PRINCIPAL, PARAMS, RESULT>(
    clock: Clock,
    private val head: CustomChangesDsl? = null,
    configuration: Configuration = default()
) : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, CustomChangesDsl>(clock, configuration)
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    final override suspend fun changes(init: suspend CustomChangesDsl.() -> RESULT): Changes<RESULT> {
        return if (head == null) {
            CustomChangesDsl.changes(ChangesAccumulator(), init)
        } else {
            CustomChangesDsl.append(head, init)
        }
    }
}
