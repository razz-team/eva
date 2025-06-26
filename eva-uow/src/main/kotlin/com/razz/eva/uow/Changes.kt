package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import kotlin.collections.partition

private fun existingChangeExceptionMessage(modelId: ModelId<*>) =
    "Change for a given model [$modelId] was already registered"

abstract class Changes<R> {
    internal abstract val result: R
    internal abstract val toPersist: List<Change>
}

class ChangesAccumulator private constructor(
    private val modelChanges: Map<ModelId<out Comparable<*>>, ModelChange>,
    private val otherChanges: List<AdhocChange>,
) {
    constructor() : this(mapOf(), listOf())

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

    private fun merge(fromModels: Collection<ModelChange>, fromOther: Collection<AdhocChange>): ChangesAccumulator {
        val intoModels = LinkedHashMap(modelChanges)
        fromModels.forEach { new ->
            intoModels.merge(new.id, new) { change, succ ->
                val merged = change.merge(succ)
                checkNotNull(merged) { "Failed to merge changes for model [${change.id}]" }
            }
        }
        return ChangesAccumulator(intoModels, otherChanges + fromOther)
    }

    fun merge(after: ChangesAccumulator): ChangesAccumulator {
        return merge(after.modelChanges.values, after.otherChanges)
    }

    fun merge(after: Changes<*>): ChangesAccumulator {
        val partitioned = after.toPersist.partition { change -> change is ModelChange }
        @Suppress("UNCHECKED_CAST") val modelChanges = partitioned.first as List<ModelChange>
        @Suppress("UNCHECKED_CAST") val otherChanges = partitioned.second as List<AdhocChange>
        return merge(modelChanges, otherChanges)
    }

    fun <R> withResult(result: R): Changes<R> {
        require(modelChanges.isNotEmpty()) { "No changes to persist" }
        return RealisedChanges(result, modelChanges.values.toList())
    }

    private fun <E : ModelEvent<MID>, M : Model<MID, E>, MID : ModelId<out Comparable<*>>>
    changes(model: M, modelEvents: List<E>, changer: (M, List<E>) -> ModelChange): ChangesAccumulator {
        return when (modelChanges[model.id()]) {
            null -> ChangesAccumulator(
                LinkedHashMap(modelChanges).apply {
                    put(model.id(), changer(model, modelEvents))
                },
                otherChanges,
            )
            else -> throw IllegalStateException(existingChangeExceptionMessage(model.id()))
        }
    }
}

internal class RealisedChanges<R>(
    override val result: R,
    override val toPersist: List<Change>
) : Changes<R>()
