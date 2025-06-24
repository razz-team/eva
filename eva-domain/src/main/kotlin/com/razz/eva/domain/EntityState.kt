package com.razz.eva.domain

import com.razz.eva.domain.EntityState.DirtyState.Companion.dirtyState
import com.razz.eva.domain.Version.Companion.V0

sealed class EntityState<ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>>(
    protected val version: Version,
    events: Collection<E>,
) : EntityStateMixin<ID, E>, Versioned {

    protected val occurredEvents = events.toList()

    final override fun version(): Version = version

    override fun writeEvents(drive: ModelEventDrive<E>): ModelEventDrive<E> {
        return drive.with(occurredEvents)
    }

    override fun isNew(): Boolean = this is NewState<ID, E>

    override fun isDirty(): Boolean = this is DirtyState<ID, E>

    override fun isPersisted(): Boolean = this is PersistentState<ID, E>

    internal fun raiseEvent(firstEvent: E, vararg newEvents: E): EntityState<ID, E> =
        raiseEvent(listOf(firstEvent, *newEvents))

    internal fun raiseEvent(newEvent: E): EntityState<ID, E> =
        raiseEvent(listOf(newEvent))

    private fun raiseEvent(newEvents: List<E>): EntityState<ID, E> {
        check(newEvents.isNotEmpty()) {
            "new events should be present"
        }
        return raiseEvent0(newEvents)
    }

    protected abstract fun raiseEvent0(newEvents: List<E>): EntityState<ID, E>

    class PersistentState<ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>> private constructor(
        version: Version,
    ) : EntityState<ID, E>(version, listOf()) {

        init {
            check(version() != V0 && occurredEvents.isEmpty()) {
                "version should be greater then 0, and events should not occurred"
            }
        }

        override fun raiseEvent0(newEvents: List<E>): DirtyState<ID, E> {
            return dirtyState(version, newEvents)
        }

        companion object {
            fun <ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>> persistentState(
                version: Version,
            ): PersistentState<ID, E> {
                return PersistentState(version)
            }
        }
    }

    class DirtyState<ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>> private constructor(
        version: Version,
        events: Collection<E>,
    ) : EntityState<ID, E>(version, events) {

        init {
            check(occurredEvents.isNotEmpty()) {
                "at least one event should occurred"
            }
        }

        override fun raiseEvent0(newEvents: List<E>): DirtyState<ID, E> {
            return DirtyState(version, occurredEvents + newEvents)
        }

        companion object {
            internal fun <ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>> dirtyState(
                version: Version,
                events: Collection<E>,
            ): DirtyState<ID, E> {
                return DirtyState(version, events)
            }
        }
    }

    class NewState<ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>> private constructor(
        events: Collection<E>,
    ) : EntityState<ID, E>(V0, events) {

        init {
            check(version() == V0 && occurredEvents.isNotEmpty()) {
                "version should be 0, and at least one event should occurred"
            }
        }

        override fun raiseEvent0(newEvents: List<E>): NewState<ID, E> {
            return NewState(occurredEvents + newEvents)
        }

        companion object {
            fun <ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>, C : ModelCreatedEvent<ID>> newState(
                createdEvent: C,
            ): NewState<ID, E> {
                @Suppress("UNCHECKED_CAST")
                return NewState(listOf(createdEvent as E))
            }
        }
    }
}

interface EntityStateMixin<MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>> : Versioned {

    fun writeEvents(drive: ModelEventDrive<E>): ModelEventDrive<E>

    fun isNew(): Boolean

    fun isDirty(): Boolean

    fun isPersisted(): Boolean

    override fun version(): Version
}
