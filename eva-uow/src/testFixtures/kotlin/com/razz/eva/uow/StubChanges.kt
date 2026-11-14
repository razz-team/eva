package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId

/**
 * Builds the [Changes] a TEST DOUBLE of a UoW returns (a mocked composed child, a stubbed executor
 * call). The changes DSL is deliberately unusable for doubles: it refuses an empty change set, which
 * breeds throwaway "anchor" registrations.
 *
 * A stub declares child behaviour instead of performing it, so every model reachable from [result]
 * (through iterables, maps, arrays, pairs, triples and an aggregate's owned models) is registered as an unchanged claim
 * automatically; [alsoRegistered] adds models the double claims the child registered without
 * returning. Under composition the merge treats these claims as claims only: they never demote
 * changes the parent already accumulated, and they vouch for exactly the claimed instances. The
 * executor rejects a stub returned by a real UoW's `tryPerform`. Under composition a stub is
 * accepted, since standing in for a composed child is what this exists for; what keeps a stub out of
 * production is this source set, which ships in the test-fixtures jar and not in the main one.
 *
 * A stub carries [PersistOrder.Registration], whatever the child's configuration says, so it carries no
 * order up. A child with a [PersistOrder.Custom] order carries it up to its parent in production; a parent's
 * spec over a stub of it sees the parent's own order instead: registration order, unless the parent has a
 * custom order of its own.
 */
@TestDoubleApi
fun <R> stubChanges(result: R, vararg alsoRegistered: Model<*, *>): Changes<R> {
    val models = LinkedHashMap<ModelId<out Comparable<*>>, Model<*, *>>()
    for (model in modelsIn(result) + alsoRegistered) {
        models.putIfAbsent(model.id(), model)
    }
    val changes = models.values.map { model ->
        @Suppress("UNCHECKED_CAST")
        NoopModel(model as Model<ModelId<out Comparable<*>>, ModelEvent<ModelId<out Comparable<*>>>>)
    }
    return StubbedChanges(result, changes)
}

// Claims only, and no accumulator: by that the executor refuses a stub as a real outcome, and a composable parent
// merges it as claims.
private class StubbedChanges<R>(
    override val result: R,
    override val modelChangesToPersist: List<ModelChange>,
) : Changes<R>() {
    override val entityChangesToPersist: List<EntityChange> get() = listOf()
    override val accumulator: ChangesAccumulator? get() = null
    override val persistOrder: PersistOrder get() = PersistOrder.Registration
}
