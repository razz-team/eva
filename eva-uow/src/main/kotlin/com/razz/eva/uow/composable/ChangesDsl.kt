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
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.PersistOrder
import com.razz.eva.uow.PersistedLookup
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.checkBuiltBy
import io.opentelemetry.api.trace.Span
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.EXACTLY_ONCE
import kotlin.contracts.contract
import kotlin.reflect.KClass

class ChangesDsl internal constructor(
    private val executionContext: ExecutionContext,
    private val uowName: String,
    persistOrder: PersistOrder,
) {
    internal val changes: ChangesAccumulator =
        ChangesAccumulator.seededFrom(executionContext.inheritedChanges, uowName)
    // var: assigned by roundtrip { } mid-block; null means no roundtrip, so the executor default-roundtrips.
    private var resultBuilder: ((PersistedLookup) -> Any?)? = null

    // The order the block's changes are written in: the unit's own, until a composed child carries a custom one up.
    // [orderFrom] names the unit whose configuration set it, for the refusal of a child with another one.
    private var persistOrder: PersistOrder = persistOrder
    private var orderFrom: String = uowName

    private fun <R> withResult(result: R): Changes<R> = changes.withResult(result, resultBuilder, persistOrder)

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
        changes.adding(model)
        return model
    }

    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        changes.updating(model)
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
    @OptIn(ExperimentalContracts::class)
    fun <MID, E, M, R> update(model: M, mutate: context(Witness<MID>) M.() -> R): R
        where M : Model<MID, E>, R : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        contract { callsInPlace(mutate, EXACTLY_ONCE) }
        val mutated = context(Witness<MID>()) { model.mutate() }
        if (mutated === model) {
            changes.declining(model)
            return mutated
        }
        check(mutated.id() == model.id()) {
            "update(model) { } returned model [${mutated.id().stringValue()}] instead of the mutated " +
                "[${model.id().stringValue()}]"
        }
        return update(mutated)
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
    @OptIn(ExperimentalContracts::class)
    fun <MID, E, M> add(create: context(Witness<MID>) () -> M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        contract { callsInPlace(create, EXACTLY_ONCE) }
        return add(context(Witness<MID>()) { create() })
    }

    fun <MID, E, M> notChanged(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        changes.claimingUnchanged(model)
        return model
    }

    fun <E : CreatableEntity> add(entity: E): E {
        changes.addingEntity(entity)
        return entity
    }

    fun <E : UpdatableEntity> update(entity: E): E {
        changes.updatingEntity(entity)
        return entity
    }

    fun <E : DeletableEntity> delete(entity: E): E {
        changes.deletingEntity(entity)
        return entity
    }

    inline fun <reified E : DeletableEntity, K : EntityKey<E>> delete(key: K): K {
        deleteByKeyInternal(key, E::class)
        return key
    }

    @PublishedApi
    internal fun <E : DeletableEntity, K : EntityKey<E>> deleteByKeyInternal(key: K, entityClass: KClass<E>) {
        changes.deletingEntityByKey(key, entityClass)
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
              UOW : ComposableUow = execute(uowFactory, principal, params) { _, _ -> }

    /**
     * Runs a child as the public `execute` does, and passes the child's name and changes to [beforeMerge] before they
     * are merged into this block's changes. If [beforeMerge] throws, this block's changes stay as they were.
     */
    internal suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        uowFactory: (ExecutionContext) -> UOW,
        principal: PRINCIPAL,
        params: InstantiationContext.Internal.() -> PARAMS,
        beforeMerge: (childName: String, childChanges: Changes<RESULT>) -> Unit,
    ): RESULT
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *>,
              UOW : ComposableUow {
        val uow = uowFactory(executionContext.withInheritedChanges(changes.handOff()))
        // the bounds already demand ComposableUow; an unchecked cast of the factory, or a class that carries both
        // markers under a suppressed compiler error, can still get past them
        check(ComposableUow::class.isInstance(uow) && !NotComposableUow::class.isInstance(uow)) {
            "${uow.name().ifEmpty { "A child unit of work" }} is not composable: a composable parent executes only " +
                "a unit of work that implements ComposableUow"
        }
        val span = uowSpan(uow.name())
        return span.use {
            val subChanges = performingSpan(uow.name()).use {
                uow.tryPerform(principal, params(InstantiationContext.Internal(0)))
            }
            checkBuiltBy(uow, subChanges, uow.name())
            span.setAttribute(
                MODEL_ID,
                subChanges.modelChangesToPersist.map { it.id.stringValue() },
            )
            beforeMerge(uow.name(), subChanges)
            // refused before the merge, so a parent that catches the refusal holds none of the child's rows
            val carried = carriedOrder(uow.name(), subChanges.persistOrder)
            changes.merging(uow.name(), subChanges)
            if (carried != persistOrder) {
                persistOrder = carried
                orderFrom = uow.name()
            }
            // Under composition the caller gets this in-memory seed; the child's resultBuilder is not rerun.
            subChanges.result
        }
    }

    // A child's configuration is not used under a parent, except a custom persist order: it carries up, so the
    // parent writes the rows of the whole tree in it. A child in Registration or Unspecified leaves the parent's
    // order. Another custom order than the parent's (its own, or one a sibling carried up) is refused, and so is a
    // custom one under an Unspecified parent: the tree has one order or none.
    private fun carriedOrder(childName: String, childOrder: PersistOrder): PersistOrder = when (childOrder) {
        PersistOrder.Registration -> persistOrder
        PersistOrder.Unspecified -> persistOrder
        is PersistOrder.Custom -> when (val own = persistOrder) {
            PersistOrder.Registration -> childOrder
            is PersistOrder.Custom -> own.also {
                check(own == childOrder) { conflictingOrders(childName, childOrder) }
            }
            PersistOrder.Unspecified -> error(conflictingOrders(childName, childOrder))
        }
    }

    private fun conflictingOrders(childName: String, childOrder: PersistOrder) =
        "${uowName.ifEmpty { "An anonymous unit of work" }} refuses composed unit of work " +
            "[${childName.ifEmpty { "anonymous" }}]: it carries persist order [$childOrder] (its own, or one of its " +
            "children's), and [${orderFrom.ifEmpty { "anonymous" }}] writes in [$persistOrder]. A composition writes " +
            "in one order: give its units one PersistOrder.Custom, a named object or a data class. An order built " +
            "for each unit, such as an anonymous object or a capturing lambda, equals only itself"

    companion object {
        internal suspend inline fun <R> changes(
            executionContext: ExecutionContext,
            uowName: String,
            persistOrder: PersistOrder,
            @Suppress("REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE")
            init: suspend ChangesDsl.() -> R,
        ): Changes<R> {
            val dsl = ChangesDsl(executionContext, uowName, persistOrder)
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
