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
    // The accumulator a change block built these changes from, so a parent can take the child's registrations
    // and see where the child started. Null only for stubChanges: the executor refuses that as a real outcome.
    internal open val accumulator: ChangesAccumulator? get() = null
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

/**
 * The changes a change block has registered so far. It is immutable: a block hands it to a composed child
 * and takes back what the child returns, so a child cannot change the parent's copy behind its back.
 *
 * The rules for how a registration combines with what is already here live in this class, so every change
 * block applies the same ones. The DSLs call [adding], [updating], [claimingUnchanged], [declining],
 * [merging] and the entity operations. The model operations and the flatten of an aggregate's owned models
 * pick a change's kind from its model's state, and [merging] takes only changes a child built the same way.
 * So an [AddModel] always holds a new model and an [UpdateModel] a changed one. Only a [NoopModel] claim can
 * disagree with its model, because `stubChanges` builds claims without a check.
 *
 * [inheritedIds] are the ids that came from outside the block that holds this value: the parent's changes a
 * composed child starts from, and every id a merged child registered. [seed] is the parent's value a composed
 * child started from, so [merging] can tell a child that started from its parent's changes from one that did
 * not. The block may extend a model it got
 * this way, so an update for an inherited id merges into the change held for it, and `notChanged` for it
 * keeps that change. Any other second registration of an id fails.
 */
internal class ChangesAccumulator private constructor(
    private val modelChanges: Map<ModelId<out Comparable<*>>, ModelChange>,
    private val entityChanges: List<EntityChange>,
    private val inheritedIds: Set<ModelId<out Comparable<*>>>,
    private val seed: ChangesAccumulator?,
) {
    internal constructor() : this(mapOf(), listOf(), setOf(), null)

    private fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withAddedModel(model: M): ChangesAccumulator {
        return modelChanges(model, ::AddModel)
    }

    private fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withUpdatedModel(model: M): ChangesAccumulator {
        return modelChanges(model, ::UpdateModel)
    }

    private fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withUnchangedModel(model: M): ChangesAccumulator {
        return modelChanges(model) { m, _ -> NoopModel(m) }
    }

    internal fun <E : CreatableEntity>
    addingEntity(entity: E): ChangesAccumulator {
        return ChangesAccumulator(modelChanges, entityChanges + AddEntity(entity), inheritedIds, seed)
    }

    internal fun <E : UpdatableEntity>
    updatingEntity(entity: E): ChangesAccumulator {
        return ChangesAccumulator(modelChanges, entityChanges + UpdateEntity(entity), inheritedIds, seed)
    }

    internal fun <E : DeletableEntity>
    deletingEntity(entity: E): ChangesAccumulator {
        return ChangesAccumulator(modelChanges, entityChanges + DeleteEntity(entity), inheritedIds, seed)
    }

    internal fun <E : DeletableEntity, K : EntityKey<E>>
    deletingEntityByKey(key: K, entityClass: KClass<E>): ChangesAccumulator {
        val deletion = DeleteEntityByKey(key, entityClass)
        return ChangesAccumulator(modelChanges, entityChanges + deletion, inheritedIds, seed)
    }

    /** Registers [model] as new. Fails when it is not new, or when its id is already registered. */
    internal fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    adding(model: M): ChangesAccumulator {
        require(model.isNew()) {
            "Attempted to register ${if (model.isDirty()) "changed" else "unchanged"} " +
                "model [${model.id().stringValue()}] as new"
        }
        return withAddedModel(model)
    }

    /**
     * Registers [model] as changed. For an inherited id it replaces the change held for it. [model] must carry
     * every event of that change and at least one more, compared by identity, or the merge fails. The merged
     * change is an add when [model] is new and an update otherwise. That keeps the kind of the held change:
     * a model only gains events in its own state, so a later state of an added model is new, and of an
     * updated one is changed. A claim over a model that is still new comes from a stub, and production would
     * insert it, so it becomes an add. For any other id [model] must be dirty and its id unregistered.
     */
    internal fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    updating(model: M): ChangesAccumulator {
        val existing = modelChanges[model.id()]
        if (existing == null || model.id() !in inheritedIds) {
            require(model.isDirty()) {
                "Attempted to register ${if (model.isNew()) "new" else "unchanged"} " +
                    "model [${model.id().stringValue()}] as changed"
            }
            return withUpdatedModel(model)
        }
        val newEvents = model.modelEvents()
        check(newEvents isSuccessorOf existing.modelEvents) {
            if (newEvents isSameAs existing.modelEvents) {
                "No-op update for model [${model.id().stringValue()}]: no new events on top of the " +
                    "existing change. Use notChanged(...) or guard update(...)."
            } else {
                "Failed to merge changes for model [${model.id().stringValue()}]"
            }
        }
        // more events than the held change, so at least one: the model is new or changed, never clean
        val merged = if (model.isNew()) AddModel(model, newEvents) else UpdateModel(model, newEvents)
        return withReplacedModelChange(model.id(), merged)
    }

    /**
     * Registers [model] as unchanged. An inherited id that already has a change keeps that change, so a
     * write registered outside this block still persists. Otherwise [model] must be persisted and clean.
     */
    internal fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    claimingUnchanged(model: M): ChangesAccumulator {
        if (model.id() in modelChanges && model.id() in inheritedIds) return this
        require(model.isPersisted()) {
            "Attempted to register ${if (model.isNew()) "new" else "changed"} " +
                "model [${model.id().stringValue()}] as unchanged"
        }
        return withUnchangedModel(model)
    }

    /**
     * Registers [model] for `update(model) { }` when its lambda hands back the receiver itself. The first
     * rule that applies decides:
     * - a registration already covers [model] (see [isCoveredBy]), the parent's included: nothing changes;
     * - the id is inherited: [model] carries an unwitnessed mutation of a model this block received, so it
     *   merges into that model's change;
     * - [model] is new and an aggregate here owns it, as this instance or a later one: it is added on its own,
     *   so it persists even if that aggregate later stops owning it;
     * - [model] is new otherwise: this fails, because a new model is registered with `add`;
     * - [model] is dirty from an unwitnessed mutator: it still carries its own write, so it is an update;
     * - an aggregate here owns a clean [model]: the aggregate persists whatever it changed, and an unchanged
     *   claim would contradict it, so nothing changes;
     * - otherwise [model] is registered as unchanged.
     */
    internal fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    declining(model: M): ChangesAccumulator {
        val registered = modelChanges[model.id()]
        val owned = ownedInstanceOf(model.id())
        return when {
            registered != null && model.isCoveredBy(registered.model) -> this
            model.id() in inheritedIds -> updating(model)
            model.isNew() && owned != null && model.isCoveredBy(owned) -> adding(model)
            model.isNew() -> error(
                "update(model) { } handed back new model [${model.id().stringValue()}] unchanged; register a " +
                    "new model with add",
            )
            model.isDirty() -> updating(model)
            owned != null -> this
            else -> claimingUnchanged(model)
        }
    }

    internal fun changeFor(modelId: ModelId<out Comparable<*>>): ModelChange? = modelChanges[modelId]

    // The instance an aggregate registered here owns for modelId. It walks owned models without the
    // flatten's contradiction checks, so it answers in any intermediate state of a block.
    private fun ownedInstanceOf(modelId: ModelId<out Comparable<*>>): Model<*, *>? {
        fun find(model: Model<*, *>): Model<*, *>? = when (model) {
            is Aggregate<*, *> -> model.ownedModels().firstNotNullOfOrNull { owned ->
                if (owned.id() == modelId) owned else find(owned)
            }
            else -> null
        }
        return modelChanges.values.firstNotNullOfOrNull { find(it.model) }
    }

    /**
     * True when the change set persists every write [model] carries: the change for its id holds [model] or a
     * later state of it. It reads the flattened view [withResult] persists, so an owned child of a registered
     * [Aggregate] counts. Reading the raw registrations alone would report a dropped write for a model the
     * aggregate persists.
     */
    internal fun persistsWritesOf(model: Model<*, *>): Boolean =
        flattened[model.id()]?.let { model.isCoveredBy(it.model) } ?: false

    // flattened once, on the first question: the value is immutable, and a caller asks for each model it holds
    private val flattened by lazy { flattenChildModels().associateBy { it.id } }

    private fun withReplacedModelChange(
        modelId: ModelId<out Comparable<*>>,
        change: ModelChange,
    ): ChangesAccumulator {
        return ChangesAccumulator(
            LinkedHashMap(modelChanges).apply { put(modelId, change) },
            entityChanges,
            inheritedIds,
            seed,
        )
    }

    /**
     * Takes in a composed child's outcome. A child that registered nothing leaves this value as it is.
     *
     * A stub has no accumulator. Its claims join the set and never demote a change held here; a later
     * update over the claim of a model that is still new makes it an add (see [updating]).
     *
     * Any other child built its changes with a change block, and that block must have started from this
     * value: the parent's changes are then all in the child's, extended or kept as they were, so the child's
     * registrations replace this value's. A child built over another ExecutionContext started from something
     * else, and would drop what the parent registered, so it fails. The child's registrations are taken
     * as it made them, before its aggregates' owned models are listed: the flatten runs once, over the
     * block that persists or hands the changes on, as it does for a single block.
     *
     * Every id of the result counts as inherited from then on, the ones this block registered before the
     * child ran included: the child received them and may have extended them.
     */
    internal fun merging(uowName: String, subChanges: Changes<*>): ChangesAccumulator {
        if (subChanges.modelChangesToPersist.isEmpty() && subChanges.entityChangesToPersist.isEmpty()) return this
        val child = subChanges.accumulator
        if (child == null) {
            val mergedModels = LinkedHashMap(modelChanges)
            for (change in subChanges.modelChangesToPersist) {
                mergedModels.putIfAbsent(change.id, change)
            }
            return ChangesAccumulator(mergedModels, entityChanges, mergedModels.keys, seed)
        }
        check(child.seed === this) {
            // an anonymous UoW has an empty simple name
            "Composed ${uowName.ifEmpty { "child UnitOfWork" }} did not start from its parent's changes; " +
                "construct the child with the ExecutionContext given to the factory"
        }
        return ChangesAccumulator(child.modelChanges, child.entityChanges, child.modelChanges.keys, seed)
    }

    internal fun <R> withResult(
        result: R,
        resultBuilder: ((PersistedLookup) -> Any?)? = null,
    ): Changes<R> {
        require(modelChanges.isNotEmpty() || entityChanges.isNotEmpty()) { "No changes to persist" }
        return RealisedChanges(result, flattenChildModels(), entityChanges, resultBuilder, this)
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
                inheritedIds,
                seed,
            )
            else -> throw IllegalStateException(existingChangeExceptionMessage(model.id()))
        }
    }

    companion object {
        /**
         * The value a composed child's block starts from: the parent's changes, with every id marked as
         * inherited. A block that runs on its own, with no parent, starts empty.
         */
        internal fun seededFrom(parent: ChangesAccumulator?): ChangesAccumulator = when (parent) {
            null -> ChangesAccumulator()
            else -> ChangesAccumulator(parent.modelChanges, parent.entityChanges, parent.modelChanges.keys, parent)
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
    override val resultBuilder: ((PersistedLookup) -> Any?)?,
    override val accumulator: ChangesAccumulator?,
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
