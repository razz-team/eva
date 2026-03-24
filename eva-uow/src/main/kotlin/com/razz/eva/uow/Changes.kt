package com.razz.eva.uow

import com.razz.eva.domain.Aggregate
import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.UpdatableEntity
import kotlin.reflect.KClass

private fun existingChangeExceptionMessage(modelId: ModelId<*>) =
    "Change for a given model [${modelId.stringValue()}] was already registered"

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
        return modelChanges(model, ::AddModel)
    }

    fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withUpdatedModel(model: M): ChangesAccumulator {
        return modelChanges(model, ::UpdateModel)
    }

    fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withUnchangedModel(model: M): ChangesAccumulator {
        return modelChanges(model) { m, _ -> NoopModel(m) }
    }

    fun <E : CreatableEntity>
    withAddedEntity(entity: E): ChangesAccumulator {
        return ChangesAccumulator(modelChanges, entityChanges + AddEntity(entity))
    }

    fun <E : DeletableEntity>
    withDeletedEntity(entity: E): ChangesAccumulator {
        return ChangesAccumulator(modelChanges, entityChanges + DeleteEntity(entity))
    }

    fun <E : UpdatableEntity>
    withUpdatedEntity(entity: E): ChangesAccumulator {
        return ChangesAccumulator(modelChanges, entityChanges + UpdateEntity(entity))
    }

    fun <E : UpdatableEntity, K : EntityKey<E>>
    withUpdatedEntityByKey(key: K, entityClass: KClass<E>): ChangesAccumulator {
        return ChangesAccumulator(modelChanges, entityChanges + UpdateEntityByKey(key, entityClass))
    }

    fun <E : DeletableEntity, K : EntityKey<E>>
    withDeletedEntityByKey(key: K, entityClass: KClass<E>): ChangesAccumulator {
        return ChangesAccumulator(modelChanges, entityChanges + DeleteEntityByKey(key, entityClass))
    }

    internal fun changeFor(modelId: ModelId<out Comparable<*>>): ModelChange? = modelChanges[modelId]

    internal fun withReplacedModelChange(
        modelId: ModelId<out Comparable<*>>,
        change: ModelChange,
    ): ChangesAccumulator {
        return ChangesAccumulator(
            LinkedHashMap(modelChanges).apply { put(modelId, change) },
            entityChanges,
        )
    }

    internal fun modelIds(): Set<ModelId<out Comparable<*>>> = modelChanges.keys

    fun <R> withResult(result: R): Changes<R> {
        require(modelChanges.isNotEmpty() || entityChanges.isNotEmpty()) { "No changes to persist" }
        return RealisedChanges(result, flattenChildModels(), entityChanges)
    }

    @Suppress("UNCHECKED_CAST")
    private fun flattenChildModels(): List<ModelChange> {
        val result = mutableListOf<ModelChange>()
        val seen = modelChanges.keys.toMutableSet()
        fun flatten(model: Model<*, *>) {
            if (model !is Aggregate<*, *>) return
            for (child in model.ownedModels()) {
                if (!seen.add(child.id())) continue
                val m = child as Model<ModelId<out Comparable<*>>, ModelEvent<ModelId<out Comparable<*>>>>
                when {
                    child.isNew() -> result.add(AddModel(m, m.modelEvents()))
                    child.isDirty() -> result.add(UpdateModel(m, m.modelEvents()))
                }
                flatten(child)
            }
        }
        for (change in modelChanges.values) {
            result.add(change)
            flatten(change.model)
        }
        return result
    }

    private fun <E : ModelEvent<MID>, M : Model<MID, E>, MID : ModelId<out Comparable<*>>>
    modelChanges(model: M, changer: (M, List<E>) -> ModelChange): ChangesAccumulator {
        return when (modelChanges[model.id()]) {
            null -> ChangesAccumulator(
                LinkedHashMap(modelChanges).apply {
                    put(model.id(), changer(model, model.modelEvents()))
                },
                entityChanges,
            )
            else -> throw IllegalStateException(existingChangeExceptionMessage(model.id()))
        }
    }

    companion object {
        internal fun from(changes: Changes<*>): ChangesAccumulator {
            return ChangesAccumulator(
                changes.modelChangesToPersist.associateByTo(LinkedHashMap()) { it.id },
                changes.entityChangesToPersist,
            )
        }
    }
}

internal class RealisedChanges<R>(
    override val result: R,
    override val modelChangesToPersist: List<ModelChange>,
    override val entityChangesToPersist: List<EntityChange>,
) : Changes<R>()
