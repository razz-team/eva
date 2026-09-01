package com.razz.eva.uow.composable

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.domain.UpdatableEntity
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.PersistedLookup
import com.razz.eva.uow.UowParams

/**
 * [ChangesDsl] with the model registrations returning [Accounted] instead of the model.
 *
 * The names are the DSL's own: `add`, `update`, `notChanged`, `delete`, `roundtrip`, `execute`, so a
 * block reads exactly as it did. What changes is only the type at the return site: a block that has to
 * produce an `Accounted<RESULT>` cannot end on an unregistered model.
 *
 * Entity changes and [execute] hand back what they always did. An entity is not the thing that gets
 * silently dropped, and a composed child UoW accounted for its own result rather than this block doing
 * it. [roundtrip] also passes through bare: its lookup falls back to the argument for models absent from
 * the change set, so wrapping its result would claim evidence the lookup does not give.
 */
class ProvingChangesDsl internal constructor(
    @PublishedApi internal val dsl: ChangesDsl,
) {

    fun <MID, E, M> add(model: M): Accounted<M>
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        Accounted(dsl.add(model), this)

    fun <MID, E, M> update(model: M): Accounted<M>
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        Accounted(dsl.update(model), this)

    fun <MID, E, M> notChanged(model: M): Accounted<M>
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        Accounted(dsl.notChanged(model), this)

    /**
     * The stated exception: a result that is not a bare model, such as a computed value, a report, or
     * a collection assembled from registered models. Spelling it at the return site is the point; a
     * reviewer sees the claim "no model here needed registering" instead of an absence. The models
     * reachable from the block's final value are verified against the change set when the block
     * completes: an unregistered new or dirty one fails the UoW.
     */
    fun <R> noModelResult(result: R): Accounted<R> = Accounted(result, this)

    /** The stated exception for a block whose UoW result is [Unit]: the registrations happened above. */
    fun noModelResult(): Accounted<Unit> = Accounted(Unit, this)

    @Deprecated(
        "A model result must be registered through add / update / notChanged, not stated as noModelResult",
        level = DeprecationLevel.ERROR,
    )
    fun <M : Model<*, *>> noModelResult(result: M): Accounted<M> =
        throw UnsupportedOperationException("A model result must be registered, not stated as noModelResult")

    fun <E : CreatableEntity> add(entity: E): E = dsl.add(entity)

    fun <E : UpdatableEntity> update(entity: E): E = dsl.update(entity)

    fun <E : DeletableEntity> delete(entity: E): E = dsl.delete(entity)

    inline fun <reified E : DeletableEntity, K : EntityKey<E>> delete(key: K): K = dsl.delete<E, K>(key)

    /**
     * Passes through bare: end the block with `noModelResult(roundtrip { ... })`. A builder returning
     * [Accounted] is refused, because the builder is rerun over the persisted set at top level and its
     * value is handed to the caller as the UoW result; a wrapper there corrupts the declared result
     * type. The refusal is wrapped around the builder itself, so it also fires on the executor's
     * post-flush rerun, not only on the eager seed.
     */
    fun <R> roundtrip(build: (p: PersistedLookup) -> R): R = dsl.roundtrip { p ->
        val built = build(p)
        check(built !is Accounted<*>) {
            "roundtrip builder must return the bare result; end the block with noModelResult(roundtrip { ... })"
        }
        built
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
              UOW : ComposableUow = dsl.execute(uowFactory, principal, params)
}
