package com.razz.eva.examples.changes.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesAccumulator

class CustomChangesDsl internal constructor(private var changes: ChangesAccumulator) {

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        changes = changes.withAdded(model)
        return model
    }

    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        changes = changes.withUpdated(model)
        return model
    }

    class ChainOfUpdatesDsl<MID, E, M> internal constructor(private var acc: M)
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {

        fun also(modelMutator: M.() -> M?): M {
            acc = acc.modelMutator() ?: acc
            return acc
        }
    }

    fun <MID, E, M> updateIfChanged(model: M, init: ChainOfUpdatesDsl<MID, E, M>.() -> M): M?
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        val dsl = ChainOfUpdatesDsl(model)
        val res = dsl.init()
        return if (res === model) {
            changes = changes.withUnchanged(model)
            null
        } else {
            changes = changes.withUpdated(res)
            res
        }
    }

    fun <MID, E, M> notChanged(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        changes = changes.withUnchanged(model)
        return model
    }

    companion object {
        internal suspend inline fun <R> changes(
            changes: ChangesAccumulator,
            @Suppress("REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE")
            init: suspend CustomChangesDsl.() -> R,
        ): Changes<R> {
            val dsl = CustomChangesDsl(changes)
            val res = init(dsl)
            return dsl.changes.withResult(res)
        }
    }
}
