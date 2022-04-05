package com.razz.eva.domain

abstract class Model<ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>>(
    private val id: ID,
    private val entityState: EntityState<ID, E>
) : Identifiable<ID>, EntityStateMixin<ID, E> by entityState {

    override fun id(): ID = id

    protected fun entityState(): EntityState<ID, E> = entityState
}

inline fun <reified M : Model<*, *>, T> M.changeIfPresent(
    value: T?,
    update: M.(T) -> M
): M {
    return value?.let { update(it) } ?: this
}
