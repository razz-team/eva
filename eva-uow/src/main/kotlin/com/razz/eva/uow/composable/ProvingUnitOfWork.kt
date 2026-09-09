package com.razz.eva.uow.composable

import com.razz.eva.domain.Model
import com.razz.eva.domain.Principal
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.modelsIn
import com.razz.eva.uow.verifyResultAccounted

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
 * Registered means the same instance, not merely the same id and events. Two clean instances of one
 * id always share an event list, since [com.razz.eva.domain.ModelState.PersistentState] starts empty
 * and stays empty, so an id-and-events rule cannot tell them apart at all; and a model declared as a
 * `data class` exposes a synthesized `copy` that carries the same state, so a dirty instance can
 * diverge in its fields while sharing its events.
 *
 * This is stricter than the framework's own contract, where a model result is resolved by id, and
 * stricter than [com.razz.eva.uow.verify.UowSpec]'s `addsAndReturns`, which serves the plain family
 * too and so can only assert that contract. The extra strictness costs one false positive: at top
 * level, with `returnRoundtrippedModels` on and a bare model or collection result, the executor would
 * have replaced a superseded instance by id anyway. It is exact everywhere else, including composed
 * children, results wrapped in a map, pair, triple or array, which the executor does not roundtrip,
 * and `returnRoundtrippedModels = false`.
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
        verifyResultAccounted(changes.result, changes.modelChangesToPersist)
        verifyResultModels(changes)
        return changes
    }

    // The proving family's strict addition on top of the universal withResult net (which already
    // rejected unregistered new or dirty models): a model with a registered id must be the registered
    // instance, so stale or smuggled instances cannot pose as the persisted state. Identity, not id
    // and events: clean instances of one id share an empty event list by construction, so that rule
    // is vacuous for them, and a data-class model's synthesized copy keeps the same state while its
    // fields diverge. Verified against the built change set: modelChangesToPersist is the flattened
    // list that will actually be persisted, so owned children of a registered Aggregate count as
    // registered.
    private fun verifyResultModels(changes: Changes<RESULT>) {
        val registered = changes.modelChangesToPersist.associateBy { it.id }
        for (model in modelsIn(changes.result)) {
            val change = registered[model.id()] ?: continue
            check(change.model === model) {
                "Model [${model.id().stringValue()}] in the result is not the instance that was " +
                    "registered: the change holds ${describe(change.model)}, the result holds " +
                    "${describe(model)}. Return the value add or update handed back, or resolve the " +
                    "registered instance with roundtrip { p -> p(model) }."
            }
        }
    }

    private fun describe(model: Model<*, *>): String {
        val state = when {
            model.isNew() -> "new"
            model.isDirty() -> "changed"
            else -> "unchanged"
        }
        val events = model.modelEvents().map { it.eventName() }
        // identity and value both matter here: the two instances often agree on class, state and
        // events, which is exactly the case this check exists for
        return "${model::class.simpleName}[$state, events = $events, " +
            "instance = ${System.identityHashCode(model)}, value = $model]"
    }
}
