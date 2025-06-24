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

    private fun merge(from: Collection<Change>): ChangesAccumulator {
        val into = LinkedHashMap(changes)
        from.forEach { new ->
            into.merge(new.id, new) { change, succ ->
                val merged = change.merge(succ)
                checkNotNull(merged) { "Failed to merge changes for model [${change.id}]" }
            }
        }
        return ChangesAccumulator(into)
    }

    fun modelIds(): Set<ModelId<out Comparable<*>>> = changes.keys

    fun modelChanges(): List<String> = changes.keys.map { changes.getValue(it)::class.simpleName!! }

    fun merge(after: ChangesAccumulator): ChangesAccumulator = merge(after.changes.values)

    fun merge(after: Changes<*>): ChangesAccumulator = merge(after.toPersist)

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
