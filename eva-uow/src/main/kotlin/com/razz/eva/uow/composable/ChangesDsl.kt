package com.razz.eva.uow.composable

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.domain.Model
import com.razz.eva.domain.UpdatableEntity
import com.razz.eva.domain.Witness
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.uow.OtelAttributes.MODEL_ID
import com.razz.eva.tracing.getEvaTracer
import com.razz.eva.tracing.use
import com.razz.eva.uow.AddModel
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.PersistedLookup
import com.razz.eva.uow.isCoveredBy
import com.razz.eva.uow.isSameAs
import com.razz.eva.uow.isSuccessorOf
import com.razz.eva.uow.UpdateModel
import com.razz.eva.uow.UowParams
import io.opentelemetry.api.trace.Span
import kotlin.reflect.KClass

class ChangesDsl internal constructor(
    private val executionContext: ExecutionContext,
) {
    private var changes: ChangesAccumulator = executionContext.inheritedChanges ?: ChangesAccumulator()
    private val inheritedModelIds: MutableSet<ModelId<out Comparable<*>>> = changes.modelIds().toMutableSet()
    // var: assigned by roundtrip { } mid-block; null means no roundtrip, so the executor default-roundtrips.
    private var resultBuilder: ((PersistedLookup) -> Any?)? = null

    private fun <R> withResult(result: R): Changes<R> = changes.withResult(result, resultBuilder)

    /**
     * Builds the UoW result via [p], which resolves each model to its change-set instance by id. Top-level
     * execution reruns this over the persisted (flushed) set, so the result carries DB-roundtripped models,
     * including ones registered by composed child UoWs. The value built eagerly here (in-memory) is the seed
     * returned under composition, where only the top-level UoW's builder is rerun.
     */
    fun <R> roundtrip(build: (p: PersistedLookup) -> R): R {
        resultBuilder = build
        return build(PersistedLookup { changes.changeFor(it)?.model })
    }

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        require(model.isNew()) {
            "Attempted to register ${if (model.isDirty()) "changed" else "unchanged"} " +
                "model [${model.id().stringValue()}] as new"
        }
        changes = changes.withAddedModel(model)
        return model
    }

    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        val existing = changes.changeFor(model.id())
        if (existing != null && model.id() in inheritedModelIds) {
            val newEvents = model.modelEvents()
            check(newEvents isSuccessorOf existing.modelEvents) {
                if (newEvents isSameAs existing.modelEvents) {
                    "No-op update for model [${model.id().stringValue()}]: no new events on top of the " +
                        "existing change. Use notChanged(...) or guard update(...)."
                } else {
                    "Failed to merge changes for model [${model.id().stringValue()}]"
                }
            }
            val merged = when (existing) {
                is AddModel<*, *, *> -> AddModel(model, newEvents)
                is UpdateModel<*, *, *> -> UpdateModel(model, newEvents)
                // a claim over a model that is still new comes from a stub; production would insert it
                is NoopModel -> if (model.isNew()) AddModel(model, newEvents) else UpdateModel(model, newEvents)
            }
            changes = changes.withReplacedModelChange(model.id(), merged)
        } else {
            require(model.isDirty()) {
                "Attempted to register ${if (model.isNew()) "new" else "unchanged"} " +
                    "model [${model.id().stringValue()}] as changed"
            }
            changes = changes.withUpdatedModel(model)
        }
        return model
    }

    /**
     * Registration as a scope: mints a [Witness] for the model's id type, runs [mutate] on the model under
     * it and registers what comes back, so the receiver's mutation cannot be left unregistered. A mutator
     * built on [com.razz.eva.domain.Model.raise] is callable only here, in [add] or in a fixture's
     * `mutating { }`. The lambda names the resulting state, so a transition is typed as its target; a
     * mutation that declines hands the receiver back (`mutate() ?: this`): a clean receiver is registered
     * as unchanged, a receiver already dirty from an unwitnessed mutator is registered as changed, one a
     * registration already covers (the parent's included) adds nothing, one a composed child received from
     * its parent and extended is merged into it, a new one an aggregate in the change set owns (as this
     * instance or a later one) is added, and a clean one such an aggregate owns is left to that aggregate.
     * The witness covers the id type, not the instance: a second model of the same type mutated inside
     * [mutate] is not registered, and the returned instance is checked by id only.
     */
    fun <MID, E, M, R> update(model: M, mutate: context(Witness<MID>) M.() -> R): R
        where M : Model<MID, E>, R : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        val mutated = context(Witness<MID>()) { model.mutate() }
        if (mutated === model) return registerDeclined(model)
        check(mutated.id() == model.id()) {
            "update(model) { } returned model [${mutated.id().stringValue()}] instead of the mutated " +
                "[${model.id().stringValue()}]"
        }
        return update(mutated)
    }

    private fun <MID, E, M> registerDeclined(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        val registered = changes.changeFor(model.id())
        val owned = changes.ownedInstanceOf(model.id())
        return when {
            // already registered as this instance or a later one, the parent's registration included
            registered != null && model.isCoveredBy(registered.model) -> model
            // a model the parent registered, extended here by an unwitnessed mutator: merge it
            model.id() in inheritedModelIds -> update(model)
            // a new model an aggregate in the change set owns, as this instance or a later one: registered on
            // its own, so it persists even if that aggregate later stops owning it; the owned copy is then
            // covered by it, or persists in its place when later
            model.isNew() && owned != null && model.isCoveredBy(owned) -> add(model)
            model.isNew() -> error(
                "update(model) { } handed back new model [${model.id().stringValue()}] unchanged; register a " +
                    "new model with add",
            )
            // dirty from an unwitnessed mutator: it still carries its own write
            model.isDirty() -> update(model)
            // a clean read of a model an aggregate in the change set owns: the aggregate persists whatever it
            // changed, and an unchanged claim here would contradict it
            owned != null -> model
            else -> notChanged(model)
        }
    }

    /**
     * Creation as a scope: a factory built on [com.razz.eva.domain.ModelState.NewState.Companion.created]
     * needs a [Witness] for its id type, and the model it returns here is added. The witness covers the
     * created model's id type, inferred from the lambda, so a stray mutation of another model type inside
     * [create] does not compile. [update] supplies the same witness for its own type, so a factory of
     * that type also compiles inside `update(model) { }`, where nothing adds what it creates. When the
     * expected type is wider than the model (`val x: Any = add { }`), inference fails: name the model's
     * type on the receiving value.
     */
    fun <MID, E, M> add(create: context(Witness<MID>) () -> M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        add(context(Witness<MID>()) { create() })

    fun <MID, E, M> notChanged(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        val existing = changes.changeFor(model.id())
        if (existing == null || model.id() !in inheritedModelIds) {
            require(model.isPersisted()) {
                "Attempted to register ${if (model.isNew()) "new" else "changed"} " +
                    "model [${model.id().stringValue()}] as unchanged"
            }
            changes = changes.withUnchangedModel(model)
        }
        return model
    }

    fun <E : CreatableEntity> add(entity: E): E {
        changes = changes.withAddedEntity(entity)
        return entity
    }

    fun <E : UpdatableEntity> update(entity: E): E {
        changes = changes.withUpdatedEntity(entity)
        return entity
    }

    fun <E : DeletableEntity> delete(entity: E): E {
        changes = changes.withDeletedEntity(entity)
        return entity
    }

    inline fun <reified E : DeletableEntity, K : EntityKey<E>> delete(key: K): K {
        deleteByKeyInternal(key, E::class)
        return key
    }

    @PublishedApi
    internal fun <E : DeletableEntity, K : EntityKey<E>> deleteByKeyInternal(key: K, entityClass: KClass<E>) {
        changes = changes.withDeletedEntityByKey(key, entityClass)
    }

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        uowFactory: (ExecutionContext) -> UOW,
        principal: PRINCIPAL,
        params: InstantiationContext.Internal.() -> PARAMS,
    ): RESULT
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *>,
              UOW : ComposableUow {
        val uow = uowFactory(executionContext.withInheritedChanges(changes))
        val span = uowSpan(uow.name())
        return span.use {
            val subChanges = performingSpan(uow.name()).use {
                uow.tryPerform(principal, params(InstantiationContext.Internal(0)))
            }
            span.setAttribute(
                MODEL_ID,
                subChanges.modelChangesToPersist.map { it.id.stringValue() },
            )
            if (subChanges.modelChangesToPersist.isNotEmpty() || subChanges.entityChangesToPersist.isNotEmpty()) {
                // Additive merge: the accumulated changes always survive, so a child built with a
                // context other than the one given to the factory can add changes but cannot make
                // inherited ones vanish; a same-model conflict on diverged event streams fails loudly.
                changes = changes.merging(uow.name(), subChanges)
                inheritedModelIds.addAll(changes.modelIds())
            }
            // Under composition the caller gets this in-memory seed; the child's resultBuilder is not rerun.
            subChanges.result
        }
    }

    companion object {
        internal suspend inline fun <R> changes(
            executionContext: ExecutionContext,
            @Suppress("REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE")
            init: suspend ChangesDsl.() -> R,
        ): Changes<R> {
            val dsl = ChangesDsl(executionContext)
            val res = init(dsl)
            return dsl.withResult(res)
        }
    }

    private fun uowSpan(name: String): Span = executionContext.otel.getEvaTracer()
        .spanBuilder(name)
        .startSpan()

    private fun performingSpan(name: String): Span = executionContext.otel.getEvaTracer()
        .spanBuilder("$name-perform")
        .startSpan()
}
