package com.razz.eva.examples.composition.uow

import com.razz.eva.domain.Principal
import com.razz.eva.uow.BaseUnitOfWork.Configuration.Companion.default
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.WrappingUnitOfWork
import com.razz.eva.uow.composable.ChangesDsl
import com.razz.eva.uow.composable.ComposableUow

/**
 * A composable family with its own change block: its units of work write `= changes { ... }` over
 * [CustomChangesDsl], and because the family implements [ComposableUow], a composable parent can run them as
 * children.
 */
abstract class CustomUnitOfWork<PRINCIPAL, PARAMS, RESULT>(
    executionContext: ExecutionContext,
    configuration: Configuration = default(),
) : WrappingUnitOfWork<PRINCIPAL, PARAMS, RESULT, CustomChangesDsl>(executionContext, configuration),
    ComposableUow
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    final override fun wrap(dsl: ChangesDsl) = CustomChangesDsl(dsl)
}
