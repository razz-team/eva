package com.razz.eva.examples.changes.uow

import com.razz.eva.domain.Principal
import com.razz.eva.uow.BaseUnitOfWork.Configuration.Companion.default
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.WrappingUnitOfWork
import com.razz.eva.uow.composable.ChangesDsl

/**
 * A family with its own change block: its units of work write `= changes { ... }` over [CustomChangesDsl].
 * [tryPerform] returns a [WrappingUnitOfWork.Block], so a unit of work of this family cannot return
 * `noChanges()` or changes from anywhere but its own block.
 */
abstract class CustomUnitOfWork<PRINCIPAL, PARAMS, RESULT>(
    executionContext: ExecutionContext,
    configuration: Configuration = default(),
) : WrappingUnitOfWork<PRINCIPAL, PARAMS, RESULT, CustomChangesDsl>(executionContext, configuration)
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    abstract override suspend fun tryPerform(principal: PRINCIPAL, params: PARAMS): Block

    final override fun wrap(dsl: ChangesDsl) = CustomChangesDsl(dsl)
}
