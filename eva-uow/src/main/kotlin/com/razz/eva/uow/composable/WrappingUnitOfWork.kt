package com.razz.eva.uow.composable

import com.razz.eva.domain.Principal
import com.razz.eva.uow.BaseUnitOfWork.Configuration.Companion.default
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.UowParams

/**
 * A [com.razz.eva.uow.WrappingUnitOfWork] whose units of work also run as composed children: a composable parent
 * can `execute` them, and what they register merges into the parent's changes. The block starts from the changes
 * the parent hands in, as in [UnitOfWork].
 */
abstract class WrappingUnitOfWork<PRINCIPAL, PARAMS, RESULT, C : Any>(
    executionContext: ExecutionContext,
    configuration: Configuration = default(),
) : com.razz.eva.uow.WrappingUnitOfWork<PRINCIPAL, PARAMS, RESULT, C>(executionContext, configuration),
    ComposableUow
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any
