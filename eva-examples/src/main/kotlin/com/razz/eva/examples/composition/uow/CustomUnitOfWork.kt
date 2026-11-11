package com.razz.eva.examples.composition.uow

import com.razz.eva.uow.BaseUnitOfWork.Configuration.Companion.default
import com.razz.eva.uow.Changes
import com.razz.eva.domain.Principal
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.composable.UnitOfWork

/**
 * A composable family with its own change block. It extends eva's composable [UnitOfWork], so its units of
 * work run as children of any composable parent, and [customChanges] runs inside the inherited
 * `changes { }`. The block needs its own name: a second `changes` that differs only in the receiver of its
 * lambda makes every `changes { }` call ambiguous.
 */
abstract class CustomUnitOfWork<PRINCIPAL, PARAMS, RESULT>(
    executionContext: ExecutionContext,
    configuration: Configuration = default(),
) : UnitOfWork<PRINCIPAL, PARAMS, RESULT>(executionContext, configuration)
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    protected suspend fun customChanges(init: suspend CustomChangesDsl.() -> RESULT): Changes<RESULT> {
        return changes { CustomChangesDsl(this).init() }
    }
}
