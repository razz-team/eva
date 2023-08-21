package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal

class ChangesDsl internal constructor(private var changes: ChangesAccumulator) {

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        changes = changes.withAdded(model)
        return model
    }

    fun <MID, E, M> update(model: M, required: Boolean = false): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        changes = when (required) {
            true -> {
                changes.withUpdated(model)
            }
            false -> {
                if (model.isDirty()) {
                    changes.withUpdated(model)
                } else {
                    changes.withUnchanged(model)
                }
            }
        }
        return model
    }

    fun <MID, E, M> notChanged(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        changes = changes.withUnchanged(model)
        return model
    }

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        uow: UOW,
        principal: PRINCIPAL,
        params: () -> PARAMS,
    ): RESULT
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS, *>,
              RESULT : Any,
              UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        val subChanges = uow.tryPerform(principal, params())
        changes = changes.merge(subChanges)
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
            return dsl.changes.withResult(res)
        }
    }
}
