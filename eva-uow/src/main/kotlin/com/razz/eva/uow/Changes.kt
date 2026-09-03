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
    // Set by stubChanges: the executor refuses a stub as a real UoW's outcome.
    internal open val stubbed: Boolean get() = false
}

/**
 * Resolves a model to its change-set instance by id: the flushed instance post-flush (top-level run),
 * the in-memory one under composition. Returns the argument unchanged when its id is not in the change set.
 *
 * The resolved instance can be a different subtype than the argument, because a composed child may have
 * moved the model to another state. [invoke] is reified, so that is checked against the type the call
 * site asks for: declare the state the change set holds, or a supertype of it, and a mismatch fails
 * here with an explanation instead of surfacing as a cast error somewhere else.
 */
class PersistedLookup internal constructor(
    @PublishedApi internal val resolveById: (ModelId<out Comparable<*>>) -> Model<*, *>?,
) {
    inline operator fun <reified M : Model<*, *>> invoke(model: M): M {
        val resolved = resolveById(model.id()) ?: return model
        check(resolved is M) {
            "Model [${model.id().stringValue()}] resolves to ${resolved::class.simpleName} in the " +
                "change set, which is not the ${M::class.simpleName} this lookup was asked for. " +
                "A composed change moved the model to another state: ask for that state, or for a " +
                "type both share."
        }
        return resolved
    }
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
     * Folds a composed child's outcome into the accumulated changes. A claim-only child (every change
     * a [NoopModel] and no entity changes, the shape of a stubbed test double) merges additively:
     * claims for new ids join the set, claims for known ids never demote the accumulated change. A
     * child that made real changes must have seeded from this accumulator, so every accumulated model
     * change must come back with its events preserved as a prefix and every accumulated entity change
     * must survive; the child's set is then the continuation of this one and replaces it wholesale,
     * preserving order.
     */
    internal fun merging(uowName: String, subChanges: Changes<*>): ChangesAccumulator {
        val claimOnly = subChanges.entityChangesToPersist.isEmpty() &&
            subChanges.modelChangesToPersist.all { it is NoopModel }
        if (claimOnly) {
            val mergedModels = LinkedHashMap(modelChanges)
            for (change in subChanges.modelChangesToPersist) {
                mergedModels.putIfAbsent(change.id, change)
            }
            return ChangesAccumulator(mergedModels, entityChanges)
        }
        val childModels = subChanges.modelChangesToPersist.associateBy { it.id }
        for ((id, prev) in modelChanges) {
            val next = childModels[id]
            val intact = next != null &&
                (next.modelEvents isSameAs prev.modelEvents || next.modelEvents isSuccessorOf prev.modelEvents)
            check(intact) {
                "Composed $uowName dropped inherited changes for model [${id.stringValue()}]; " +
                    "construct the child with the ExecutionContext given to the factory"
            }
        }
        check(subChanges.entityChangesToPersist.containsAll(entityChanges)) {
            "Composed $uowName dropped inherited entity changes; " +
                "construct the child with the ExecutionContext given to the factory"
        }
        return ChangesAccumulator(
            subChanges.modelChangesToPersist.associateByTo(LinkedHashMap()) { it.id },
            subChanges.entityChangesToPersist,
        )
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
    // A NoopModel claim vouches only for the claimed instance; a real change vouches for its id.
    private fun verifyResultAccounted(result: Any?, flattened: List<ModelChange>) {
        val registered = flattened.associateBy { it.id }
        for (model in modelsIn(result)) {
            val change = registered[model.id()]
            val accounted = change != null && (change !is NoopModel || change.model === model)
            if (accounted) continue
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
    override val stubbed: Boolean = false,
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
