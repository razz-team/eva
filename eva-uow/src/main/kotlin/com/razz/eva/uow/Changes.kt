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
    "Change for a given model [${modelId.stringValue()}] was already registered. The block registered it twice, " +
        "and the second registration would replace the first and drop its write. Register a model once: make " +
        "every mutation first, then call add or update with the last instance. A second update is accepted only " +
        "for a model the parent or a composed child registered"

abstract class Changes<R> {
    internal abstract val result: R
    // A block's changes are the ones its accumulator lists. Only a stub, which has no accumulator, lists its own.
    internal open val modelChangesToPersist: List<ModelChange>
        get() = checkNotNull(accumulator).modelChangesToPersist
    internal open val entityChangesToPersist: List<EntityChange>
        get() = checkNotNull(accumulator).entityChangesToPersist
    // Builder set by roundtrip { }; the executor runs it over persisted models. Any? since R is known only per call.
    internal open val resultBuilder: ((PersistedLookup) -> Any?)? get() = null
    // The accumulator a change block built these changes from, so a parent can take the child's registrations
    // and see where the child started. Null only for stubChanges: the executor refuses that as a real outcome.
    internal abstract val accumulator: ChangesAccumulator?
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
 * composed child starts from, and every id a merged child registered. The block may extend a model it got
 * this way, so an update for an inherited id merges into the change held for it, and `notChanged` for it
 * keeps that change. Any other second registration of an id fails.
 *
 * [seed] is the parent's value a composed child started from. [merging] compares it with the parent's value
 * when the child returns, and so tells a child that started from its parent's changes from one that did not.
 */
internal class ChangesAccumulator private constructor(
    private val modelChanges: Map<ModelId<out Comparable<*>>, ModelChange>,
    private val entityChanges: List<EntityChange>,
    private val inheritedIds: Set<ModelId<out Comparable<*>>>,
    private val seed: ChangesAccumulator?,
) {
    internal constructor() : this(mapOf(), listOf(), setOf(), null)

    // every later value of a block is built here, so none can forget where the block started
    private fun next(
        modelChanges: Map<ModelId<out Comparable<*>>, ModelChange>,
        entityChanges: List<EntityChange>,
        inheritedIds: Set<ModelId<out Comparable<*>>>,
    ) = ChangesAccumulator(modelChanges, entityChanges, inheritedIds, seed)

    private fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withAddedModel(model: M): ChangesAccumulator {
        return modelChanges(model) { m, _ -> AddModel(m) }
    }

    private fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withUpdatedModel(model: M): ChangesAccumulator {
        return modelChanges(model) { m, _ -> UpdateModel(m) }
    }

    private fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withUnchangedModel(model: M): ChangesAccumulator {
        return modelChanges(model) { m, _ -> NoopModel(m) }
    }

    internal fun <E : CreatableEntity>
    addingEntity(entity: E): ChangesAccumulator {
        return next(modelChanges, entityChanges + AddEntity(entity), inheritedIds)
    }

    internal fun <E : UpdatableEntity>
    updatingEntity(entity: E): ChangesAccumulator {
        return next(modelChanges, entityChanges + UpdateEntity(entity), inheritedIds)
    }

    internal fun <E : DeletableEntity>
    deletingEntity(entity: E): ChangesAccumulator {
        return next(modelChanges, entityChanges + DeleteEntity(entity), inheritedIds)
    }

    internal fun <E : DeletableEntity, K : EntityKey<E>>
    deletingEntityByKey(key: K, entityClass: KClass<E>): ChangesAccumulator {
        val deletion = DeleteEntityByKey(key, entityClass)
        return next(modelChanges, entityChanges + deletion, inheritedIds)
    }

    /** Registers [model] as new. Fails when it is not new, or when its id is already registered. */
    internal fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    adding(model: M): ChangesAccumulator {
        require(model.isNew()) {
            "Attempted to register ${if (model.isDirty()) "changed" else "unchanged"} " +
                "model [${model.id().stringValue()}] as new. It is stored already, so inserting it would fail. " +
                "Register a stored model with update, or with notChanged when it did not change"
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
                    "model [${model.id().stringValue()}] as changed. " +
                    if (model.isNew()) {
                        "It is not stored yet, so there is nothing to update. Register a new model with add"
                    } else {
                        "It carries no change, so a mutation the block meant to make is missing. Call " +
                            "notChanged(model), or update(model) { mutate() ?: this } to register whichever happened"
                    }
            }
            return withUpdatedModel(model)
        }
        val newEvents = model.modelEvents()
        check(newEvents isSuccessorOf existing.modelEvents) {
            if (newEvents isSameAs existing.modelEvents) {
                "No-op update for model [${model.id().stringValue()}]: no new events on top of the " +
                    "existing change. Use notChanged(...) or guard update(...)."
            } else {
                "Failed to merge changes for model [${model.id().stringValue()}]: the parent or a composed " +
                    "child changed this model before, and the instance given to update does not carry those " +
                    "changes, so writing it would drop them. It was most likely read again by id. Update the " +
                    "instance the parent passed in (constantModelParam), or the one execute or " +
                    "roundtrip { p -> p(model) } returned"
            }
        }
        // more events than the held change, so at least one: the model is new or changed, never clean
        val merged = if (model.isNew()) AddModel(model) else UpdateModel(model)
        return withReplacedModelChange(model.id(), merged)
    }

    /**
     * Registers [model] as unchanged. An inherited id that already has a change keeps that change, so a
     * write registered outside this block still persists. [model] must then carry no write that change
     * lacks: the instance it holds, a clean read, or an earlier state of it. A model with a write of its own
     * fails, because the claim would drop that write. Otherwise [model] must be persisted and clean.
     */
    internal fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    claimingUnchanged(model: M): ChangesAccumulator {
        val held = modelChanges[model.id()]
        if (held != null && model.id() in inheritedIds) {
            check(model.isCoveredBy(held.model)) {
                "notChanged got model [${model.id().stringValue()}] with a write the change its parent " +
                    "registered does not carry: the write would be silently dropped. Register it with update, " +
                    "or call notChanged with the instance the parent registered"
            }
            return this
        }
        require(model.isPersisted()) {
            "Attempted to register ${if (model.isNew()) "new" else "changed"} " +
                "model [${model.id().stringValue()}] as unchanged. It carries a write, and notChanged would " +
                "drop it. Register a new model with add and a changed one with update"
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
     * The model changes this value persists: the registrations and the owned models of their aggregates, as
     * [flattenChildModels] lists them. Flattened once, on the first question, since the value is immutable.
     */
    internal val modelChangesToPersist: List<ModelChange> by lazy { flattenChildModels() }

    internal val entityChangesToPersist: List<EntityChange> get() = entityChanges

    // by id, since a caller asks for each model it holds
    private val flattened by lazy { modelChangesToPersist.associateBy { it.id } }

    /**
     * True when the change set persists every write [model] carries: the change for its id holds [model] or a
     * later state of it. It reads the flattened view [modelChangesToPersist], so an owned child of a registered
     * [Aggregate] counts. Reading the raw registrations alone would report a dropped write for a model the
     * aggregate persists.
     */
    internal fun persistsWritesOf(model: Model<*, *>): Boolean =
        flattened[model.id()]?.let { model.isCoveredBy(it.model) } ?: false

    private fun withReplacedModelChange(
        modelId: ModelId<out Comparable<*>>,
        change: ModelChange,
    ): ChangesAccumulator {
        return next(LinkedHashMap(modelChanges).apply { put(modelId, change) }, entityChanges, inheritedIds)
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
     * else, and would drop what the parent registered, so it fails. So does a child whose parent registered
     * something after the child started, from the params lambda or while the child ran: the child's changes
     * lack that registration. The child's registrations are taken
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
            return next(mergedModels, entityChanges, mergedModels.keys)
        }
        check(child.seed === this) {
            // an anonymous UoW has an empty simple name
            "Composed ${uowName.ifEmpty { "child UnitOfWork" }} did not start from the changes its parent holds " +
                "now; a child extends composable.UnitOfWork or a WrappingUnitOfWork family and not the plain " +
                "UnitOfWork, it is constructed with the ExecutionContext given to the factory, and its parent " +
                "does not register from the params lambda or while the child runs"
        }
        return next(child.modelChanges, child.entityChanges, child.modelChanges.keys)
    }

    internal fun <R> withResult(
        result: R,
        resultBuilder: ((PersistedLookup) -> Any?)?,
    ): Changes<R> {
        // The flatten runs here, so an owned model that contradicts a registration fails where the block ends.
        // Its list is empty exactly when no model is registered.
        require(modelChangesToPersist.isNotEmpty() || entityChanges.isNotEmpty()) {
            "No changes to persist: the changes { } block registered nothing, which usually means a mutation was " +
                "not passed to add or update and would be lost. Register what the block wrote with add or update, " +
                "and a model it read and kept with notChanged(model). When there is nothing to write, return " +
                "noChanges(result) in place of changes { }"
        }
        return AccumulatedChanges(result, resultBuilder, this)
    }

    private fun flattenChildModels(): List<ModelChange> {
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

    private fun changeOf(model: Model<*, *>): ModelChange? = when {
        model.isNew() -> AddModel(model)
        model.isDirty() -> UpdateModel(model)
        else -> null
    }

    private fun <E : ModelEvent<MID>, M : Model<MID, E>, MID : ModelId<out Comparable<*>>>
    modelChanges(model: M, changer: (M, List<E>) -> ModelChange): ChangesAccumulator {
        return when (modelChanges[model.id()]) {
            null -> next(
                LinkedHashMap(modelChanges).apply { put(model.id(), changer(model, model.modelEvents())) },
                entityChanges,
                inheritedIds,
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

/**
 * The changes a change block built, or the empty ones of `noChanges`: the result, the builder `roundtrip { }` set,
 * and the accumulator, which lists the model and entity changes.
 */
internal class AccumulatedChanges<R>(
    override val result: R,
    override val resultBuilder: ((PersistedLookup) -> Any?)?,
    override val accumulator: ChangesAccumulator,
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
