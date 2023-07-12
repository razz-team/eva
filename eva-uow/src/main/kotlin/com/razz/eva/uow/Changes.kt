package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId

private fun existingChangeExceptionMessage(modelId: ModelId<*>) =
    "Change for a given model [$modelId] was already registered"

abstract class Changes<R> {
    internal abstract val result: R
    internal abstract val toPersist: List<Change>
}

class ChangesAccumulator private constructor(
    private val changes: Map<ModelId<out Comparable<*>>, Change>
) {
    constructor() : this(emptyMap())

    fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withAdded(model: M): ChangesAccumulator {
        val eventDrive = model.writeEvents(ModelEventDrive())
        return changes(model, eventDrive.events(), ::Add)
    }

    fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withUpdated(model: M): ChangesAccumulator {
        val eventDrive = model.writeEvents(ModelEventDrive())
        return changes(model, eventDrive.events(), ::Update)
    }

    fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withUnchanged(model: M): ChangesAccumulator {
        return changes(model, emptyList()) { m, _ -> Noop(m) }
    }

    fun merge(after: Changes<*>): ChangesAccumulator {
        val merging = LinkedHashMap(changes)
        after.toPersist.forEach { new ->
            merging.merge(new.id, new) { change, succ ->
                val merged = change.merge(succ)
                checkNotNull(merged) { "Failed to merge changes for model [${change.id}]" }
            }
        }
        return ChangesAccumulator(merging)
    }

    fun <R> withResult(result: R): Changes<R> {
        require(changes.isNotEmpty()) { "No changes to persist" }
        return RealisedChanges(result, changes.values.toList())
    }

    private fun <E : ModelEvent<MID>, M : Model<MID, E>, MID : ModelId<out Comparable<*>>>
    changes(model: M, modelEvents: List<E>, changer: (M, List<E>) -> Change): ChangesAccumulator {
        return when (changes[model.id()]) {
            null -> ChangesAccumulator(
                LinkedHashMap(changes).apply {
                    put(model.id(), changer(model, modelEvents))
                }
            )
            else -> throw IllegalStateException(existingChangeExceptionMessage(model.id()))
        }
    }
}

internal class RealisedChanges<R>(
    override val result: R,
    override val toPersist: List<Change>
) : Changes<R>()
