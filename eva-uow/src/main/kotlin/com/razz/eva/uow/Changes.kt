package com.razz.eva.uow

import com.razz.eva.domain.Aggregate
import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.domain.Model
import com.razz.eva.domain.UpdatableEntity
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import kotlin.reflect.KClass

private fun existingChangeExceptionMessage(modelId: ModelId<*>) =
    "Change for a given model [${modelId.stringValue()}] was already registered"

abstract class Changes<R> {
    internal abstract val result: R
    internal abstract val modelChangesToPersist: List<ModelChange>
    internal abstract val entityChangesToPersist: List<EntityChange>
    // Builder set by roundtrip { }; the executor runs it over persisted models. Any? since R is known only per call.
    internal open val resultBuilder: ((PersistedLookup) -> Any?)? get() = null
}

/**
 * Resolves a model to its change-set instance by id: the flushed instance post-flush (top-level run),
 * the in-memory one under composition. Returns the argument unchanged when its id is not in the change set.
 */
interface PersistedLookup {
    operator fun <M : Model<*, *>> invoke(model: M): M
}

// One PersistedLookup over a by-id resolver: persisted map at top level, in-memory change set under composition.
internal class ChangeSetLookup(
    private val resolve: (ModelId<out Comparable<*>>) -> Model<*, *>?,
) : PersistedLookup {
    @Suppress("UNCHECKED_CAST")
    override fun <M : Model<*, *>> invoke(model: M): M = (resolve(model.id()) ?: model) as M
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

    fun <E : UpdatableEntity>
    withUpdatedEntity(entity: E): ChangesAccumulator {
        return ChangesAccumulator(modelChanges, entityChanges + UpdateEntity(entity))
    }

    fun <E : DeletableEntity>
    withDeletedEntity(entity: E): ChangesAccumulator {
        return ChangesAccumulator(modelChanges, entityChanges + DeleteEntity(entity))
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

    /**
     * Merges a composed child's changes additively: the accumulated changes always survive, so a child
     * constructed with the wrong `ExecutionContext` can add changes but can no longer make inherited
     * ones vanish. The one irreconcilable case is the same model changed on two diverged event
     * streams, which fails loudly instead of silently clobbering.
     */
    internal fun merging(uowName: String, subChanges: Changes<*>): ChangesAccumulator {
        val mergedModels = LinkedHashMap(modelChanges)
        for (change in subChanges.modelChangesToPersist) {
            val prev = mergedModels[change.id]
            if (prev != null && change is NoopModel) {
                // the child's "unchanged" claim never demotes an accumulated change
                continue
            }
            val intact = prev == null ||
                change.modelEvents isSameAs prev.modelEvents ||
                change.modelEvents isSuccessorOf prev.modelEvents
            check(intact) {
                "Composed $uowName produced conflicting changes for model [${change.id.stringValue()}]; " +
                    "construct the child with the ExecutionContext given to the factory"
            }
            mergedModels[change.id] = change
        }
        val newEntities = subChanges.entityChangesToPersist.filter { child -> entityChanges.none { it === child } }
        return ChangesAccumulator(mergedModels, entityChanges + newEntities)
    }

    fun <R> withResult(
        result: R,
        resultBuilder: ((PersistedLookup) -> Any?)? = null,
    ): Changes<R> {
        require(modelChanges.isNotEmpty() || entityChanges.isNotEmpty()) { "No changes to persist" }
        val flattened = flattenChildModels()
        verifyResultAccounted(result, flattened)
        return RealisedChanges(result, flattened, entityChanges, resultBuilder)
    }

    // The universal net under every changes block, whatever the UoW family: a new or dirty model in
    // the result whose id is not in the change set carries a write that will never be persisted.
    private fun verifyResultAccounted(result: Any?, flattened: List<ModelChange>) {
        val registered = flattened.mapTo(HashSet()) { it.id }
        for (model in modelsIn(result)) {
            if (model.id() in registered) continue
            check(!model.isNew() && !model.isDirty()) {
                "Unregistered ${if (model.isNew()) "new" else "changed"} " +
                    "model [${model.id().stringValue()}] in the result: the write would be silently dropped"
            }
        }
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
    override val resultBuilder: ((PersistedLookup) -> Any?)? = null,
) : Changes<R>()

// Reference-identity prefix checks over event lists: composed changes to one model are reconcilable
// only when one list literally extends the other, which is what the DSL's merge produces.
internal infix fun List<ModelEvent<*>>.isSuccessorOf(events: List<ModelEvent<*>>): Boolean {
    if (size <= events.size) {
        return false
    }
    events.forEachIndexed { i, e ->
        if (this[i] !== e) {
            return false
        }
    }
    return true
}

internal infix fun List<ModelEvent<*>>.isSameAs(events: List<ModelEvent<*>>): Boolean {
    if (size != events.size) {
        return false
    }
    events.forEachIndexed { i, e ->
        if (this[i] !== e) {
            return false
        }
    }
    return true
}
