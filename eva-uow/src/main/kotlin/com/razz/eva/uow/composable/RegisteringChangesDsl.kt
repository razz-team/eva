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
import com.razz.eva.uow.Registered
import com.razz.eva.uow.UowParams

/**
 * [ChangesDsl] with the model registrations returning [Registered] instead of the model.
 *
 * The names are the DSL's own: `add`, `update`, `notChanged`, `delete`, `roundtrip`, `execute`, so a
 * block reads exactly as it did. What changes is only the type at the return site: a block that has to
 * produce a `Registered<RESULT>` cannot end on an unregistered model.
 *
 * Entity changes and [execute] hand back what they always did. An entity is not the thing that gets
 * silently dropped, and a composed child UoW registered its own result rather than this block doing it.
 * [roundtrip] also passes through unwrapped: its lookup falls back to the argument for models absent
 * from the change set, so wrapping its result would claim evidence the lookup does not give.
 */
class RegisteringChangesDsl internal constructor(
    @PublishedApi internal val dsl: ChangesDsl,
) {

    fun <MID, E, M> add(model: M): Registered<M>
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        Registered(dsl.add(model))

    fun <MID, E, M> update(model: M): Registered<M>
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        Registered(dsl.update(model))

    fun <MID, E, M> notChanged(model: M): Registered<M>
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        Registered(dsl.notChanged(model))

    fun <E : CreatableEntity> add(entity: E): E = dsl.add(entity)

    fun <E : UpdatableEntity> update(entity: E): E = dsl.update(entity)

    fun <E : DeletableEntity> delete(entity: E): E = dsl.delete(entity)

    inline fun <reified E : DeletableEntity, K : EntityKey<E>> delete(key: K): K = dsl.delete<E, K>(key)

    fun <R> roundtrip(build: (p: PersistedLookup) -> R): R = dsl.roundtrip(build)

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        uowFactory: (ExecutionContext) -> UOW,
        principal: PRINCIPAL,
        params: InstantiationContext.Internal.() -> PARAMS,
    ): RESULT
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *, *>,
              UOW : Composable = dsl.execute(uowFactory, principal, params)
}
