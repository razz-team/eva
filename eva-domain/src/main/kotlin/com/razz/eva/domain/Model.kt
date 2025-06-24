package com.razz.eva.domain

abstract class Model<ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>>(
    private val id: ID,
    private val entityState: EntityState<ID, E>
) : Identifiable<ID>, EntityStateMixin<ID, E> {

    final override fun isDirty(): Boolean = entityState.isDirty()
    final override fun isNew(): Boolean = entityState.isNew()
    final override fun isPersisted(): Boolean = entityState.isPersisted()
    final override fun version(): Version = entityState.version()
    final override fun writeEvents(drive: ModelEventDrive<E>): ModelEventDrive<E> = entityState.writeEvents(drive)

    override fun id(): ID = id

    protected fun raiseEvent(firstEvent: E, vararg newEvents: E): EntityState<ID, E> =
        entityState.raiseEvent(firstEvent, *newEvents)

    protected fun raiseEvent(newEvent: E): EntityState<ID, E> =
        entityState.raiseEvent(newEvent)
}
