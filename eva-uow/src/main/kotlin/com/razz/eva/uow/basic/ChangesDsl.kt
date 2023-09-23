package com.razz.eva.uow.basic

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesAccumulator

class ChangesDsl internal constructor(private var changes: ChangesAccumulator) {

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        require(model.isNew()) {
            "Attempted to register ${if (model.isDirty()) "changed" else "unchanged"} " +
                "model [${model.id().stringValue()}] as new"
        }
        changes = changes.withAdded(model)
        return model
    }

    fun <MID, E, M> update(model: M, required: Boolean = false): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        changes = when (required) {
            true -> {
                require(model.isDirty()) {
                    "Attempted to register ${if (model.isNew()) "new" else "unchanged"} " +
                        "model [${model.id().stringValue()}] as changed"
                }
                changes.withUpdated(model)
            }
            false -> {
                require(!model.isNew()) {
                    "Attempted to register new model [${model.id().stringValue()}] as changed"
                }
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
        require(model.isPersisted()) {
            "Attempted to register ${if (model.isNew()) "new" else "changed"} " +
                "model [${model.id().stringValue()}] as unchanged"
        }
        changes = changes.withUnchanged(model)
        return model
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
