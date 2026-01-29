package com.razz.eva.uow

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import kotlin.reflect.KClass

private fun existingChangeExceptionMessage(modelId: ModelId<*>) =
    "Change for a given model [$modelId] was already registered"

abstract class Changes<R> {
    internal abstract val result: R
    internal abstract val modelChangesToPersist: List<ModelChange>
    internal abstract val entityChangesToPersist: List<EntityChange>
}

class ChangesAccumulator private constructor(
    private val modelChanges: Map<ModelId<out Comparable<*>>, ModelChange>,
    private val entityChanges: List<EntityChange>,
) {
    constructor() : this(mapOf(), listOf())

    fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withAddedModel(model: M): ChangesAccumulator {
        val eventDrive = model.writeEvents(ModelEventDrive())
        return modelChanges(model, eventDrive.events(), ::AddModel)
    }

    fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withUpdatedModel(model: M): ChangesAccumulator {
        val eventDrive = model.writeEvents(ModelEventDrive())
        return modelChanges(model, eventDrive.events(), ::UpdateModel)
    }

    fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withUnchangedModel(model: M): ChangesAccumulator {
        return modelChanges(model, listOf()) { m, _ -> NoopModel(m) }
    }

    fun <E : CreatableEntity>
    withAddedEntity(entity: E): ChangesAccumulator {
        return ChangesAccumulator(modelChanges, entityChanges + AddEntity(entity))
    }

    fun <E : DeletableEntity>
    withDeletedEntity(entity: E): ChangesAccumulator {
        return ChangesAccumulator(modelChanges, entityChanges + DeleteEntity(entity))
    }

    fun <E : DeletableEntity, K : EntityKey<E>>
    withDeletedEntityByKey(key: K, entityClass: KClass<E>): ChangesAccumulator {
        return ChangesAccumulator(modelChanges, entityChanges + DeleteEntityByKey(key, entityClass))
    }

    private fun mergeModelChanges(from: Collection<ModelChange>): Map<ModelId<out Comparable<*>>, ModelChange> {
        val into = LinkedHashMap(modelChanges)
        from.forEach { new ->
            into.merge(new.id, new) { change, succ ->
                val merged = change.merge(succ)
                checkNotNull(merged) { "Failed to merge changes for model [${change.id}]" }
            }
        }
        return into
    }

    fun merge(after: ChangesAccumulator): ChangesAccumulator {
        return ChangesAccumulator(
            mergeModelChanges(after.modelChanges.values),
            entityChanges + after.entityChanges,
        )
    }

    fun merge(after: Changes<*>): ChangesAccumulator {
        return ChangesAccumulator(
            mergeModelChanges(after.modelChangesToPersist),
            entityChanges + after.entityChangesToPersist,
        )
    }

    fun <R> withResult(result: R): Changes<R> {
        require(modelChanges.isNotEmpty() || entityChanges.isNotEmpty()) { "No changes to persist" }
        return RealisedChanges(result, modelChanges.values.toList(), entityChanges)
    }

    private fun <E : ModelEvent<MID>, M : Model<MID, E>, MID : ModelId<out Comparable<*>>>
    modelChanges(model: M, modelEvents: List<E>, changer: (M, List<E>) -> ModelChange): ChangesAccumulator {
        return when (modelChanges[model.id()]) {
            null -> ChangesAccumulator(
                LinkedHashMap(modelChanges).apply {
                    put(model.id(), changer(model, modelEvents))
                },
                entityChanges,
            )
            else -> throw IllegalStateException(existingChangeExceptionMessage(model.id()))
        }
    }
}

internal class RealisedChanges<R>(
    override val result: R,
    override val modelChangesToPersist: List<ModelChange>,
    override val entityChangesToPersist: List<EntityChange>,
) : Changes<R>()
