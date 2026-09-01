package com.razz.eva.uow.composable

import com.razz.eva.domain.Model
import com.razz.eva.domain.Principal
import com.razz.eva.uow.Changes
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.UowParams

/**
 * A composable [UnitOfWork] whose change block must end on an [Accounted] value. `changes { }` persists
 * only what went through `add` / `update` / `notChanged`, so a plain UoW that returns a freshly mutated
 * model without registering it drops the write on the floor and reports success. Here that branch does
 * not compile: `changes` takes a block returning `Accounted<RESULT>`, and the only mints are
 * [ProvingChangesDsl]'s registrations and the explicit [ProvingChangesDsl.noModelResult].
 *
 * The compile-time check covers the block's tail. It is backed at runtime, before the block's value is
 * unwrapped: the evidence must have been minted by this block's own DSL, a model in the result must be
 * the registered instance, and an unregistered model in the result must be clean. A mutation discarded
 * mid-block, or a secondary model that is never referenced again, remains the author's to register.
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
        return ChangesDsl.changes(executionContext) {
            val proving = ProvingChangesDsl(this)
            val accounted = proving.init()
            check(accounted.origin === proving) {
                "Accounted evidence was minted by another changes block"
            }
            verifyResultModels(accounted.result, proving)
            accounted.result
        }
    }

    private fun verifyResultModels(result: Any?, proving: ProvingChangesDsl) {
        val models = when (result) {
            is Model<*, *> -> listOf(result)
            is Collection<*> -> result.filterIsInstance<Model<*, *>>()
            else -> listOf()
        }
        for (model in models) {
            val registered = proving.dsl.changeFor(model.id())?.model
            if (registered != null) {
                check(registered === model) {
                    "Model [${model.id().stringValue()}] in the result is not the registered instance"
                }
            } else {
                check(!model.isNew() && !model.isDirty()) {
                    "Unregistered ${if (model.isNew()) "new" else "changed"} " +
                        "model [${model.id().stringValue()}] in the result: the write would be silently dropped"
                }
            }
        }
    }
}
