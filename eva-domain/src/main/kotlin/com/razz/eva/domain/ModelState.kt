package com.razz.eva.domain

import com.razz.eva.domain.ModelState.DirtyState.Companion.dirtyState
import com.razz.eva.domain.Version.Companion.V0

sealed class ModelState<ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>>(
    protected val version: Version,
    events: Collection<E>,
) : ModelStateMixin<ID, E> {

    protected val occurredEvents = events.toList()

    final override fun version(): Version = version

    override fun writeEvents(drive: ModelEventDrive<E>): ModelEventDrive<E> {
        return drive.with(occurredEvents)
    }

    override fun isNew(): Boolean = this is NewState<ID, E>

    override fun isDirty(): Boolean = this is DirtyState<ID, E>

    override fun isPersisted(): Boolean = this is PersistentState<ID, E>

    internal fun raiseEvent(firstEvent: E, vararg newEvents: E): ModelState<ID, E> =
        raiseEvents(listOf(firstEvent, *newEvents))

    internal fun raiseEvent(newEvent: E): ModelState<ID, E> =
        raiseEvents(listOf(newEvent))

    protected abstract fun raiseEvents(newEvents: List<E>): ModelState<ID, E>

    class PersistentState<ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>> private constructor(
        version: Version,
        internal val proto: Any?,
    ) : ModelState<ID, E>(version, listOf()) {

        init {
            check(version() != V0 && occurredEvents.isEmpty()) {
                "version should be greater then 0, and events should not occurred"
            }
        }

        override fun raiseEvents(newEvents: List<E>): DirtyState<ID, E> {
            return dirtyState(version, newEvents, proto)
        }

        companion object {
            fun <ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>> persistentState(
                version: Version,
                proto: Any?,
            ): PersistentState<ID, E> {
                return PersistentState(version, proto)
            }
        }
    }

    class DirtyState<ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>> private constructor(
        version: Version,
        events: Collection<E>,
        internal val proto: Any?,
    ) : ModelState<ID, E>(version, events) {

        init {
            check(occurredEvents.isNotEmpty()) {
                "at least one event should occurred"
            }
        }

        override fun raiseEvents(newEvents: List<E>): DirtyState<ID, E> {
            return dirtyState(version, occurredEvents + newEvents, proto)
        }

        companion object {
            internal fun <ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>> dirtyState(
                version: Version,
                events: Collection<E>,
                proto: Any?,
            ): DirtyState<ID, E> {
                return DirtyState(version, events, proto)
            }
        }
    }

    class NewState<ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>> private constructor(
        events: Collection<E>,
    ) : ModelState<ID, E>(V0, events) {

        init {
            check(version() == V0 && occurredEvents.isNotEmpty()) {
                "version should be 0, and at least one event should occurred"
            }
        }

        override fun raiseEvents(newEvents: List<E>): NewState<ID, E> {
            return NewState(occurredEvents + newEvents)
        }

        companion object {
            fun <ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>, C : ModelCreatedEvent<ID>> newState(
                createdEvent: C,
            ): NewState<ID, E> {
                @Suppress("UNCHECKED_CAST")
                return NewState(listOf(createdEvent as E))
            }
            fun <ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>, C : ModelCreatedEvent<ID>> newState(
                createdEvent: C,
                vararg newEvents: E,
            ): NewState<ID, E> {
                @Suppress("UNCHECKED_CAST")
                return NewState(listOf(createdEvent as E, *newEvents))
            }
        }
    }
}

interface ModelStateMixin<MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>> : Versioned {

    fun writeEvents(drive: ModelEventDrive<E>): ModelEventDrive<E>

    fun isNew(): Boolean

    fun isDirty(): Boolean

    fun isPersisted(): Boolean

    override fun version(): Version
}
