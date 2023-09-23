package com.razz.eva.uow.composable

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.uow.UowParams

class ChangesDsl internal constructor(initial: ChangesAccumulator) {
    private var tail: ChangesAccumulator? = null
    private var head: ChangesAccumulator = initial
    private fun <R> withResult(result: R): Changes<R> {
        val snapTail = tail
        return if (snapTail == null) {
            head.withResult(result)
        } else {
            snapTail.merge(head).withResult(result)
        }
    }

    // Under no circumstances should this method accept a model that is not new
    fun <MID, E, M> add(model: M): M
    where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        require(model.isNew()) {
            "Attempted to register ${if (model.isDirty()) "changed" else "unchanged"} " +
                "model [${model.id().stringValue()}] as new"
        }
        head = head.withAdded(model)
        return model
    }

    // It is possible in a sound program to register a model as changed
    // that was created in different sub-uow and passed as a parameter, ie:
    // val uow0 = UnitOfWork0<..., Model>(...) {
    //     override fun tryPerform(...): Changes<Model> = changes { add(newModel()) }
    // }
    // val uow1 = UnitOfWork1(...) {
    //     data classParams(val model: Model)
    //     override fun tryPerform(... params: Params) = changes { ... update(params.model.modify()) }
    //     ^ there is no control whether `model` was queried from db and somehow modified prior to being passed to uow
    //       or it was created out of scope of uow and never persisted hence `::isNew` is true
    // }
    // val bigUow = UnitOfWorkBig(...) {
    //     override fun tryPerform(...) = changes {
    //         ...
    //         val model = uow0.execute(...)
    //         ...
    //         val ... = uow1.execute(...) { UnitOfWork1.Params(model) }
    //         ...
    //     }
    //
    fun <MID, E, M> update(model: M): M
    where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        require(model.isDirty() || model.isNew()) {
            "Attempted to register unchanged model [${model.id().stringValue()}] as changed"
        }
        head = head.withUpdated(model)
        return model
    }

    // It is possible in a sound program to register a model as unchanged
    // that was created in different sub-uow and passed as a parameter, ie:
    // val uow0 = UnitOfWork0<..., Model>(...) {
    //     override fun tryPerform(...): Changes<Model> = changes { add(newModel()) }
    // }
    // val uow1 = UnitOfWork1(...) {
    //     data classParams(val model: Model)
    //     override fun tryPerform(... params: Params) = changes { ... if (dontChange) { notChanged(params.model) } }
    //     ^ there is no control whether `model` was queried from db and prior to being passed to uow
    //       or it was created out of scope of uow and never persisted hence `::isNew` is true
    // }
    // val bigUow = UnitOfWorkBig(...) {
    //     override fun tryPerform(...) = changes {
    //         ...
    //         val model = uow0.execute(...)
    //         ...
    //         val ... = uow1.execute(...) { UnitOfWork1.Params(model) }
    //         ...
    //     }
    //
    fun <MID, E, M> notChanged(model: M): M
    where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        require(model.isPersisted() || model.isNew()) {
            "Attempted to register changed model [${model.id().stringValue()}] as unchanged"
        }
        head = head.withUnchanged(model)
        return model
    }

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        uow: UOW,
        principal: PRINCIPAL,
        params: () -> PARAMS,
    ): RESULT
    where PRINCIPAL : Principal<*>,
          PARAMS : UowParams<PARAMS>,
          RESULT : Any,
          UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        val subChanges = uow.tryPerform(principal, params())
        val snapTail = tail
        tail = if (snapTail == null) {
            head.merge(subChanges)
        } else {
            snapTail.merge(head.merge(subChanges))
        }
        head = ChangesAccumulator()
        return subChanges.result
    }

    companion object {
        internal suspend inline fun <R> changes(
            changes: ChangesAccumulator,
            @Suppress("REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE")
            init: suspend ChangesDsl.() -> R
        ): Changes<R> {
            val dsl = ChangesDsl(changes)
            val res = init(dsl)
            return dsl.withResult(res)
        }
    }
}
