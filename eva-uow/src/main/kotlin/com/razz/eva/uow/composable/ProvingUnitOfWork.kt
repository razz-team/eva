package com.razz.eva.uow.composable

import com.razz.eva.domain.Principal
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.modelsIn

/**
 * A composable [UnitOfWork] whose change block must end on an [Accounted] value. `changes { }` persists
 * only what went through `add` / `update` / `notChanged`, so a plain UoW that returns a freshly mutated
 * model without registering it drops the write on the floor and reports success. Here that branch does
 * not compile: `changes` takes a block returning `Accounted<RESULT>`, and the only mints are
 * [ProvingChangesDsl]'s registrations and the explicit [ProvingChangesDsl.noModelResult].
 *
 * The compile-time check covers the block's tail. It is backed at runtime once the block completes:
 * the evidence must have been minted by this block's own DSL, and every model reachable from the
 * result through iterables, maps, arrays, pairs and triples must be the registered instance or clean.
 * A mutation discarded mid-block, a secondary model that is never referenced again, or a model buried
 * in a wrapper the walk cannot see (a data class, a Sequence) remains the author's to register.
 *
 * `tryPerform` keeps the name, the parameters and the return type the base class gives it, the block
 * keeps the DSL's own names, and the block builder keeps the name `changes`. The UoW stays
 * [ComposableUow]: it can execute children and be executed as a child. Note that unlike the plain
 * family's `update(model, required)`, the composable `update` this DSL delegates to requires a dirty
 * model unless the change is inherited.
 */
abstract class ProvingUnitOfWork<PRINCIPAL, PARAMS, RESULT>(
    private val executionContext: ExecutionContext,
    configuration: Configuration = Configuration.default(),
) : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, ProvingChangesDsl>(executionContext, configuration),
    ComposableUow
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    protected suspend fun changes(
        init: suspend ProvingChangesDsl.() -> Accounted<RESULT>,
    ): Changes<RESULT> {
        val changes = ChangesDsl.changes(executionContext) {
            val proving = ProvingChangesDsl(this)
            val accounted = proving.init()
            check(accounted.origin === proving) {
                "Accounted evidence was minted by another changes block"
            }
            accounted.result
        }
        verifyResultModels(changes)
        return changes
    }

    // The proving family's strict addition on top of the universal withResult net (which already
    // rejected unregistered new or dirty models): a model with a registered id must be the registered
    // instance, so stale or smuggled instances cannot pose as the persisted state. Verified against
    // the built change set: modelChangesToPersist is the flattened list that will actually be
    // persisted, so owned children of a registered Aggregate count as registered.
    private fun verifyResultModels(changes: Changes<RESULT>) {
        val registered = changes.modelChangesToPersist.associateBy { it.id }
        for (model in modelsIn(changes.result)) {
            val change = registered[model.id()] ?: continue
            check(change.model === model) {
                "Model [${model.id().stringValue()}] in the result is not the registered instance"
            }
        }
    }
}
