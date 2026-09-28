// detekt's parser predates context parameters and misreads their colon; a per-declaration Suppress
// does not reach the context line, so the rule is off for this file
@file:Suppress("SpacingAroundColon")

package com.razz.eva.domain

abstract class Model<ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>>(
    private val id: ID,
    private val modelState: ModelState<ID, E>,
) : Identifiable<ID>, ModelStateMixin<ID, E> {

    final override fun isDirty(): Boolean = modelState.isDirty()
    final override fun isNew(): Boolean = modelState.isNew()
    final override fun isPersisted(): Boolean = modelState.isPersisted()
    final override fun version(): Version = modelState.version()
    internal fun modelEvents(): List<E> = modelState.modelEvents()

    override fun id(): ID = id

    protected fun raiseEvent(firstEvent: E, vararg newEvents: E): ModelState<ID, E> =
        modelState.raiseEvent(firstEvent, *newEvents)

    protected fun raiseEvent(newEvent: E): ModelState<ID, E> =
        modelState.raiseEvent(newEvent)

    /**
     * [raiseEvent] that can only be called under a [Witness] for this model's id type. A mutator built
     * on it declares `context(_: Witness<ID>)` and is thereby callable only inside the change block's
     * `update(model) { }` or `add(model) { }`, where its result is registered, or inside a fixture's
     * `mutating { }`. A model migrates one mutator at a time; the ungated [raiseEvent] stays for the rest.
     */
    context(_: Witness<ID>)
    protected fun raise(firstEvent: E, vararg newEvents: E): ModelState<ID, E> =
        modelState.raiseEvent(firstEvent, *newEvents)

    context(_: Witness<ID>)
    protected fun raise(newEvent: E): ModelState<ID, E> =
        modelState.raiseEvent(newEvent)

    internal fun <T> proto(): T? {
        val proto = (modelState as? ModelState.DirtyState<ID, E>)?.proto
        @Suppress("UNCHECKED_CAST")
        return proto as? T
    }

    // Nanoseconds this model has been held in memory since it was hydrated from storage, or null if it was
    // never persisted (a brand-new model). Monotonic and JVM-local; lets a holder decide if its reference is stale.
    internal fun heldNanos(): Long? = modelState.readMark?.let { System.nanoTime() - it }
}
