package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId

/**
 * Builds the [Changes] a TEST DOUBLE of a UoW returns (a mocked composed child, a stubbed executor
 * call). The changes DSL is deliberately unusable for doubles: it refuses an empty change set, which
 * breeds throwaway "anchor" registrations, and hand-assembled accumulators must re-enumerate what the
 * double is supposed to have registered.
 *
 * A stub declares child behaviour instead of performing it, so every model reachable from [result]
 * (through iterables, maps, arrays, pairs and triples) is registered as an unchanged entry
 * automatically; [alsoRegistered] adds models the double claims the child registered without
 * returning. Under composition the additive merge treats these unchanged entries as claims, never as
 * demotions of changes the parent already accumulated.
 */
fun <R> stubChanges(result: R, vararg alsoRegistered: Model<*, *>): Changes<R> {
    val models = LinkedHashMap<ModelId<out Comparable<*>>, Model<*, *>>()
    for (model in modelsIn(result) + alsoRegistered) {
        models.putIfAbsent(model.id(), model)
    }
    val changes = models.values.map { model ->
        @Suppress("UNCHECKED_CAST")
        NoopModel(model as Model<ModelId<out Comparable<*>>, ModelEvent<ModelId<out Comparable<*>>>>)
    }
    return RealisedChanges(result, changes, listOf())
}
