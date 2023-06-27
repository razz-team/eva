package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId

class ChangesDsl internal constructor(private var changes: ChangesWithoutResult) {

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

    companion object {
        internal suspend inline fun <R> changes(
            changes: ChangesWithoutResult,
            @Suppress("REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE")
            init: suspend ChangesDsl.() -> R
        ): Changes<R> {
            val dsl = ChangesDsl(changes)
            val res = init(dsl)
            return dsl.changes.withResult(res)
        }
    }
}
