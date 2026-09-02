package com.razz.eva.uow.composable

import com.razz.eva.domain.Principal
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.UowParams

/**
 * The proving family's effect-shaped member: a UoW whose result is [Unit]. With no result to carry
 * models, tail evidence certifies nothing, so the change block is free-form; what guards an effect
 * UoW is what guards every block: an empty change set is refused, the completed block's result is
 * verified (vacuously here), and registrations go through the same [ProvingChangesDsl]. A block whose
 * only statement is a discarded mutation fails loudly on "No changes to persist"; a discarded
 * mutation next to real registrations remains the author's to spot, exactly as in every family.
 *
 * Declared via `com.razz.eva.uow.proving.unit.UnitOfWork`, adoption drops the `Unit` type argument
 * along with the terminal `noModelResult()`.
 */
abstract class ProvingEffectUnitOfWork<PRINCIPAL, PARAMS>(
    private val executionContext: ExecutionContext,
    configuration: Configuration = Configuration.default(),
) : BaseUnitOfWork<PRINCIPAL, PARAMS, Unit, ProvingChangesDsl>(executionContext, configuration),
    ComposableUow
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS> {

    protected suspend fun changes(init: suspend ProvingChangesDsl.() -> Unit): Changes<Unit> {
        return ChangesDsl.changes(executionContext) {
            ProvingChangesDsl(this).init()
        }
    }
}
