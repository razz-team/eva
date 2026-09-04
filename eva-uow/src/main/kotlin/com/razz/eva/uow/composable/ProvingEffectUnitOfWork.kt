package com.razz.eva.uow.composable

import com.razz.eva.domain.Principal
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.verifyResultAccounted

/**
 * The proving family's effect-shaped member: a UoW whose result is [Unit]. Its block registers and
 * ends on evidence like any proving block, but the evidence need not be `Accounted<Unit>`, so a
 * registration is a legal tail on its own and a block that ends on a statement closes with `Unit`
 * (see [ProvingChangesDsl.Unit]). A tail that is a bare mutation does not compile, in this family as
 * in the others.
 *
 * Declared via `com.razz.eva.uow.proving.unit.UnitOfWork`, so adoption drops the `Unit` type argument.
 * A mutation discarded mid-block remains the author's to spot; downstream, Kotlin's return value
 * checker covers that case in any position.
 */
abstract class ProvingEffectUnitOfWork<PRINCIPAL, PARAMS>(
    private val executionContext: ExecutionContext,
    configuration: Configuration = Configuration.default(),
) : BaseUnitOfWork<PRINCIPAL, PARAMS, Unit, ProvingChangesDsl>(executionContext, configuration),
    ComposableUow
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS> {

    protected suspend fun changes(init: suspend ProvingChangesDsl.() -> Accounted<*>): Changes<Unit> {
        val changes = ChangesDsl.changes(executionContext) {
            val proving = ProvingChangesDsl(this)
            val accounted = proving.init()
            check(accounted.origin === proving) {
                "Accounted evidence was minted by another changes block"
            }
        }
        verifyResultAccounted(changes.result, changes.modelChangesToPersist)
        return changes
    }
}
