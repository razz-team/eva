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

    /**
     * Every change by id over the same flattened view [withResult] persists, so an owned child of a
     * registered [Aggregate] counts as accounted. The guards must agree: resolving a child through
     * the raw map alone reports a dropped write for a model the aggregate will persist.
     */
    /**
     * The instance an aggregate registered here owns for [modelId], found by walking owned models without
     * the flatten's contradiction checks, so it answers in any intermediate state of a block.
     */
    internal fun ownedInstanceOf(modelId: ModelId<out Comparable<*>>): Model<*, *>? {
        fun find(model: Model<*, *>): Model<*, *>? = when (model) {
            is Aggregate<*, *> -> model.ownedModels().firstNotNullOfOrNull { owned ->
                if (owned.id() == modelId) owned else find(owned)
            }
            else -> null
        }
        return modelChanges.values.firstNotNullOfOrNull { find(it.model) }
    }

    internal fun flattenedChanges(): Map<ModelId<out Comparable<*>>, ModelChange> =
        flattenChildModels().associateBy { it.id }

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
     * Folds a composed child's outcome into the accumulated changes. A stubbed child merges
     * additively: its claims for new ids join the set, and claims for known ids never demote the
     * accumulated change. Stubbing is read from the flag [Changes.stubbed] records rather than
     * inferred from the shape, so a real child whose only registration happens to be a claim is
     * still held to the seeding check below. A
     * child that made real changes must have seeded from this accumulator, so every accumulated model
     * change must come back with its events preserved as a prefix and every accumulated entity change
     * must survive; the child's set is then the continuation of this one and replaces it wholesale,
     * preserving order.
     */
    internal fun merging(uowName: String, subChanges: Changes<*>): ChangesAccumulator {
        // an anonymous UoW has an empty simple name
        val child = uowName.ifEmpty { "child UnitOfWork" }
        if (subChanges.stubbed) {
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
                "Composed $child dropped inherited changes for model [${id.stringValue()}]; " +
                    "construct the child with the ExecutionContext given to the factory"
            }
        }
        check(subChanges.entityChangesToPersist.containsAll(entityChanges)) {
            "Composed $child dropped inherited entity changes; " +
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
        return RealisedChanges(result, flattenChildModels(), entityChanges, resultBuilder)
    }

    internal fun flattenChildModels(): List<ModelChange> {
        val result = LinkedHashMap<ModelId<out Comparable<*>>, ModelChange>()
        // the instance each id persists as: a registration, or an owned child flattened for it
        val known: MutableMap<ModelId<out Comparable<*>>, Model<*, *>> =
            modelChanges.mapValuesTo(LinkedHashMap()) { (_, change) -> change.model }
        fun flatten(model: Model<*, *>) {
            if (model !is Aggregate<*, *>) return
            for (child in model.ownedModels()) {
                val persistsAs = known[child.id()]
                if (persistsAs != null && child.isCoveredBy(persistsAs)) continue
                val claimedUnchanged = modelChanges[child.id()] is NoopModel
                // An owned instance that is the later state of what its id persists as takes that entry's
                // place, as a root does under composition: a child extended after a composed merge, or one
                // owned by two aggregates, persists once, whichever owner comes first.
                check(persistsAs == null || persistsAs.isCoveredBy(child) && !claimedUnchanged) {
                    if (claimedUnchanged) {
                        "Model [${child.id().stringValue()}] is registered as unchanged, but aggregate " +
                            "[${model.id().stringValue()}] owns a ${if (child.isNew()) "new" else "changed"} " +
                            "instance of it: the write would be silently dropped"
                    } else {
                        "Aggregate [${model.id().stringValue()}] owns an instance of model " +
                            "[${child.id().stringValue()}] that diverges from the one it persists as: neither " +
                            "carries the other's events, so one write would be silently dropped"
                    }
                }
                known[child.id()] = child
                changeOf(child)?.let { result[child.id()] = it }
                flatten(child)
            }
        }
        for (change in modelChanges.values) {
            // a registration an earlier owner already replaced with a later instance keeps that entry, but
            // its own owned models are still walked, so none of their writes slips past the known check
            if (change.id !in result) result[change.id] = change
            flatten(change.model)
        }
        return result.values.toList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun changeOf(model: Model<*, *>): ModelChange? {
        val m = model as Model<ModelId<out Comparable<*>>, ModelEvent<ModelId<out Comparable<*>>>>
        return when {
            m.isNew() -> AddModel(m, m.modelEvents())
            m.isDirty() -> UpdateModel(m, m.modelEvents())
            else -> null
        }
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

/**
 * A new or dirty model reachable from a result whose id is not in [changes] carries a write that will
 * never be persisted. A [NoopModel] claim vouches only for the claimed instance; a real change vouches
 * for its id. Called by the executor over the final merged change set, which is where a composed
 * child's hand-back to its parent has finished and the answer is authoritative.
 */
internal fun verifyResultAccounted(result: Any?, changes: List<ModelChange>) {
    val registered = changes.associateBy { it.id }
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

/**
 * A model in the result whose id is registered must not carry a write of its own: it is either the
 * registered instance, or an ancestor of it, meaning a clean instance or one whose events the
 * registered instance extends. An ancestor is what a block holds when it returns the model it read
 * and registered the mutated one, or when a composed child mutated a model after the parent's
 * roundtrip { } seed resolved it; its events all persist through the registered instance, and the
 * executor's roundtrip hands the caller the persisted state wherever the result shape allows. A
 * sibling (a second mutation of the same read, or a copy of the registered instance that kept its
 * events while its fields diverged) carries events or fields nothing persists, so it fails. Events are compared by
 * identity. Checked over the flattened set, so an owned child of a registered Aggregate counts as
 * registered.
 */
internal fun verifyResultInstances(result: Any?, changes: List<ModelChange>) {
    val registered = changes.associateBy { it.id }
    for (model in modelsIn(result)) {
        val change = registered[model.id()] ?: continue
        check(model.isCoveredBy(change.model)) {
            "Model [${model.id().stringValue()}] in the result carries a write its registered instance " +
                "does not: the change holds ${describe(change.model)}, the result holds " +
                "${describe(model)}. Return the value add or update handed back, or resolve the " +
                "registered instance with roundtrip { p -> p(model) }."
        }
    }
}

/**
 * True when [this] carries no write that [persistsAs] lacks: it is that instance, it is clean, or
 * [persistsAs]'s events literally extend its own (an ancestor). The result nets, `noChanges` and the
 * aggregate flatten all use this one rule, so they agree on what a stale instance may be.
 */
internal fun Model<*, *>.isCoveredBy(persistsAs: Model<*, *>): Boolean =
    this === persistsAs ||
        !isNew() && !isDirty() ||
        persistsAs.modelEvents() isSuccessorOf modelEvents()

private fun describe(model: Model<*, *>): String {
    val state = when {
        model.isNew() -> "new"
        model.isDirty() -> "changed"
        else -> "unchanged"
    }
    val events = model.modelEvents().map { it.eventName() }
    // identity, not value: the two instances often agree on class, state and events, and a model's
    // toString may carry personal data into logs and spans
    return "${model::class.simpleName}[$state, events = $events, instance = ${System.identityHashCode(model)}]"
}
