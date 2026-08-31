package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId

/**
 * [ChangesDsl] with the model registrations returning [Registered] instead of the model.
 *
 * The names are the DSL's own: `add`, `update`, `notChanged`, so a block reads exactly as it did.
 * What changes is only the type at the return site: a block that has to produce a `Registered<RESULT>`
 * cannot end on an unregistered model.
 */
class RegisteringChangesDsl internal constructor(private val dsl: ChangesDsl) {

    fun <MID, E, M> add(model: M): Registered<M>
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        Registered(dsl.add(model))

    fun <MID, E, M> update(model: M, required: Boolean = false): Registered<M>
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        Registered(dsl.update(model, required))

    fun <MID, E, M> notChanged(model: M): Registered<M>
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        Registered(dsl.notChanged(model))
}
