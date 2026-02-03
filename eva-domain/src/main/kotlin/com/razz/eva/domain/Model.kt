package com.razz.eva.domain

import com.razz.eva.domain.ModelState.DirtyState
import com.razz.eva.domain.ModelState.SnapshotState
import com.razz.eva.domain.ModelState.SnapshotState.Companion.snapshotState

abstract class Model<ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>>(
    private val id: ID,
    private var modelState: ModelState<ID, E>,
) : Identifiable<ID>, ModelStateMixin<ID, E> {

    final override fun isDirty(): Boolean = modelState.isDirty()
    final override fun isNew(): Boolean = modelState.isNew()
    final override fun isPersisted(): Boolean = modelState.isPersisted()
    final override fun version(): Version = modelState.version()
    final override fun writeEvents(drive: ModelEventDrive<E>): ModelEventDrive<E> = modelState.writeEvents(drive)

    override fun id(): ID = id

    protected fun raiseEvent(firstEvent: E, vararg newEvents: E): ModelState<ID, E> =
        modelState.raiseEvent(firstEvent, *newEvents)

    protected fun raiseEvent(newEvent: E): ModelState<ID, E> =
        modelState.raiseEvent(newEvent)

    internal fun <T> proto(): T? {
        val proto = (modelState as? DirtyState<ID, E>)?.proto
        @Suppress("UNCHECKED_CAST")
        return proto as? T
    }

    internal fun wrapInSnapshotState() {
        if (modelState !is SnapshotState) {
            modelState = snapshotState(modelState)
        }
    }

    /**
     * Returns the current model state for framework-level inspection.
     */
    internal fun modelState(): ModelState<ID, E> = modelState

    /**
     * Unwraps SnapshotState if present, mutating the model's state to the underlying state.
     * Called by ChangesAccumulator before creating ModelChange so that ModelChange
     * sees the real state (New, Dirty, or Persistent) rather than the SnapshotState wrapper.
     */
    internal fun unwrapModelState() {
        val state = modelState
        if (state is SnapshotState) {
            modelState = state.unwrap()
        }
    }
}
