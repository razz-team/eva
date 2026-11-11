package com.razz.eva.examples.changes.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.uow.composable.ChangesDsl

/**
 * A family's own change block, built on the [ChangesDsl] that [CustomUnitOfWork.wrap] hands it. Every operation
 * ends in a call to the wrapped DSL, so a registration here gets the same checks as one in an eva block.
 */
class CustomChangesDsl internal constructor(private val changes: ChangesDsl) {

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        changes.add(model)

    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        changes.update(model)

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
            changes.notChanged(model)
            null
        } else {
            changes.update(res)
        }
    }

    fun <MID, E, M> notChanged(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        changes.notChanged(model)
}
