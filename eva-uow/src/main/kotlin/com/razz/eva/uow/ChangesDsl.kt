package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId

class ChangesDsl internal constructor(private val changes: ChangesAccumulator) {

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        changes.adding(model)
        return model
    }

    fun <MID, E, M> update(model: M, required: Boolean = false): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        if (!required) {
            require(!model.isNew()) {
                "Attempted to register new model [${model.id().stringValue()}] as changed"
            }
        }
        when (required || model.isDirty()) {
            true -> changes.updating(model)
            false -> changes.claimingUnchanged(model)
        }
        return model
    }

    fun <MID, E, M> notChanged(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        changes.claimingUnchanged(model)
        return model
    }

    companion object {
        internal suspend inline fun <R> changes(
            @Suppress("REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE")
            init: suspend ChangesDsl.() -> R,
        ): Changes<R> {
            val dsl = ChangesDsl(ChangesAccumulator())
            val res = init(dsl)
            return dsl.changes.withResult(res, null)
        }
    }
}
