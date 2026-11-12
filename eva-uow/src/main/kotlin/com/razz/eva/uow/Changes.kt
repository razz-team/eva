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
 * The changes a change block has registered so far. A block owns its accumulator and registers into it in place.
 * A composed child's block starts from its own copy of its parent's changes, taken from the parent's [HandOff],
 * and registers into that copy. The parent takes the child's changes over in [merging], once the child's block
 * has ended. So no two running blocks share a map, and a child cannot change the parent's changes behind its back.
 * When the block ends, [withResult] closes the accumulator: its changes stay as they were, and a later write
 * fails. A block's accumulator is not thread safe, like the unit of work that owns it.
 *
 * The rules for how a registration combines with what is already here live in this class, so every change
 * block applies the same ones. The DSLs call [adding], [updating], [claimingUnchanged], [declining],
 * [merging] and the entity operations. The model operations and the flatten of an aggregate's owned models
 * pick a change's kind from its model's state, and [merging] takes only changes a child built the same way.
 * So an [AddModel] always holds a new model and an [UpdateModel] a changed one. Only a [NoopModel] claim can
 * disagree with its model, because `stubChanges` builds claims without a check.
 *
 * An id is inherited when it came from outside the block: the parent's changes a composed child starts from, and
 * every id a merged child registered. [ownIds] are the others, the ids the block registered itself since it
 * started or last took in a child, and null while the block has inherited nothing. The block may extend
 * a model it inherited, so an update for an inherited id merges into the change held for it, and `notChanged` for
 * it keeps that change. Any other second registration of an id fails.
 *
 * [seed] is the hand-off a composed child's block started from. [merging] checks it against the parent when the
 * child returns, and so tells a child that started from its parent's changes from one that did not.
 */
internal class ChangesAccumulator private constructor(
    private var modelChanges: LinkedHashMap<ModelId<out Comparable<*>>, ModelChange>,
    private var entityChanges: ArrayList<EntityChange>,
    private val seed: HandOff?,
) {
    internal constructor() : this(LinkedHashMap(), ArrayList(), null)

    private var ownIds: HashSet<ModelId<out Comparable<*>>>? = if (seed == null) null else HashSet()

    // every write counts, so a child handed off before one can tell
    private var writes = 0

    // set when the block ends, so its changes stay as they were then
    private var closed = false

    // the flattened changes, kept until the next write
    private var persisted: List<ModelChange>? = null
    private var persistedById: Map<ModelId<out Comparable<*>>, ModelChange>? = null

    private fun wrote() {
        writes += 1
        persisted = null
        persistedById = null
    }

    private fun checkOpen() {
        check(!closed) {
            "Attempted to register after the changes { } block ended: its changes are built already, so the " +
                "registration would be lost. Register a model inside the block that persists it"
        }
    }

    private fun isInherited(modelId: ModelId<out Comparable<*>>) =
        ownIds.let { own -> own != null && modelId in modelChanges && modelId !in own }

    internal fun <E : CreatableEntity>
    addingEntity(entity: E) {
        checkOpen()
        entityChanges.add(AddEntity(entity))
        wrote()
    }

    internal fun <E : UpdatableEntity>
    updatingEntity(entity: E) {
        checkOpen()
        entityChanges.add(UpdateEntity(entity))
        wrote()
    }

    internal fun <E : DeletableEntity>
    deletingEntity(entity: E) {
        checkOpen()
        entityChanges.add(DeleteEntity(entity))
        wrote()
    }

    internal fun <E : DeletableEntity, K : EntityKey<E>>
    deletingEntityByKey(key: K, entityClass: KClass<E>) {
        checkOpen()
        entityChanges.add(DeleteEntityByKey(key, entityClass))
        wrote()
    }

    /** Registers [model] as new. Fails when it is not new, or when its id is already registered. */
    internal fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    adding(model: M) {
        checkOpen()
        require(model.isNew()) {
            "Attempted to register ${if (model.isDirty()) "changed" else "unchanged"} " +
                "model [${model.id().stringValue()}] as new. It is stored already, so inserting it would fail. " +
                "Register a stored model with update, or with notChanged when it did not change"
        }
        registering(AddModel(model))
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
    updating(model: M) {
        checkOpen()
        val existing = modelChanges[model.id()]
        if (existing == null || !isInherited(model.id())) {
            require(model.isDirty()) {
                "Attempted to register ${if (model.isNew()) "new" else "unchanged"} " +
                    "model [${model.id().stringValue()}] as changed. " +
                    if (model.isNew()) {
                        "It is not stored yet, so there is nothing to update. Register a new model with add. If " +
                            "the block added it already, make every mutation first and add the last instance"
                    } else {
                        "It carries no change, so a mutation the block meant to make is missing. Call " +
                            "notChanged(model), or register the outcome of a mutation that can decline with " +
                            "update(model) { mutate() ?: this } in a composable block; in a plain block drop " +
                            "required = true"
                    }
            }
            registering(UpdateModel(model))
            return
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
        modelChanges[model.id()] = if (model.isNew()) AddModel(model) else UpdateModel(model)
        wrote()
    }

    /**
     * Registers [model] as unchanged. An inherited id that already has a change keeps that change, so a
     * write registered outside this block still persists. [model] must then carry no write that change
     * lacks: the instance it holds, a clean read, or an earlier state of it. A model with a write of its own
     * fails, because the claim would drop that write. Otherwise [model] must be persisted and clean.
     */
    internal fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    claimingUnchanged(model: M) {
        checkOpen()
        val held = modelChanges[model.id()]
        if (held != null && isInherited(model.id())) {
            check(model.isCoveredBy(held.model)) {
                "notChanged got model [${model.id().stringValue()}] with a write the change its parent " +
                    "registered does not carry: the write would be silently dropped. Register it with update, " +
                    "or call notChanged with the instance the parent registered"
            }
            return
        }
        require(model.isPersisted()) {
            "Attempted to register ${if (model.isNew()) "new" else "changed"} " +
                "model [${model.id().stringValue()}] as unchanged. It carries a write, and notChanged would " +
                "drop it. Register a new model with add and a changed one with update"
        }
        registering(NoopModel(model))
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
    declining(model: M) {
        checkOpen()
        val registered = modelChanges[model.id()]
        val owned = ownedInstanceOf(model.id())
        when {
            registered != null && model.isCoveredBy(registered.model) -> Unit
            isInherited(model.id()) -> updating(model)
            model.isNew() && owned != null && model.isCoveredBy(owned) -> adding(model)
            model.isNew() -> error(
                "update(model) { } handed back new model [${model.id().stringValue()}] unchanged; register a " +
                    "new model with add",
            )
            model.isDirty() -> updating(model)
            owned != null -> Unit
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
     * The model changes this block persists: the registrations and the owned models of their aggregates, as
     * [flattenChildModels] lists them. Flattened on the first question after a write.
     */
    internal val modelChangesToPersist: List<ModelChange>
        get() = persisted ?: flattenChildModels().also { persisted = it }

    internal val entityChangesToPersist: List<EntityChange> get() = entityChanges

    /**
     * True when the change set persists every write [model] carries: the change for its id holds [model] or a
     * later state of it. It reads the flattened view [modelChangesToPersist], so an owned child of a registered
     * [Aggregate] counts. Reading the raw registrations alone would report a dropped write for a model the
     * aggregate persists.
     */
    internal fun persistsWritesOf(model: Model<*, *>): Boolean {
        val byId = persistedById ?: modelChangesToPersist.associateBy { it.id }.also { persistedById = it }
        return byId[model.id()]?.let { model.isCoveredBy(it.model) } ?: false
    }

    /** What a composed child starts from: this block and its writes so far. It copies nothing. */
    internal fun handOff(): HandOff = HandOff(this, writes)

    /**
     * Takes in a composed child's outcome. A child that registered nothing leaves this block as it is.
     *
     * A stub has no accumulator. Its claims join the set and never demote a change held here; a later
     * update over the claim of a model that is still new makes it an add (see [updating]).
     *
     * Any other child built its changes with a change block, and that block must have started from what this
     * block handed off, with no write here since: the parent's changes are then all in the child's, extended or
     * kept as they were, so the child's registrations replace this block's. A child built over another
     * ExecutionContext started from something else, and would drop what the parent registered, so it fails. So
     * does a child whose parent registered something after handing off, from the params lambda or while the
     * child ran: the child's changes lack that registration. The child's registrations are taken as it made
     * them, before its aggregates' owned models are listed: the flatten runs once, over the block that persists
     * or hands the changes on, as it does for a single block.
     *
     * Every id counts as inherited from then on, the ones this block registered before the child ran included:
     * the child received them and may have extended them.
     */
    internal fun merging(uowName: String, subChanges: Changes<*>) {
        checkOpen()
        if (subChanges.modelChangesToPersist.isEmpty() && subChanges.entityChangesToPersist.isEmpty()) return
        val child = subChanges.accumulator
        if (child == null) {
            for (change in subChanges.modelChangesToPersist) {
                modelChanges.putIfAbsent(change.id, change)
            }
            ownIds = HashSet()
            wrote()
            return
        }
        check(child.seed?.isFrom(this) == true) { notStartedFromParent(uowName) }
        // the child's block has ended and its changes hold this block's, so this block takes its model map over;
        // the entity list is copied, since the child's changes hand it out as they were when its block ended
        modelChanges = child.modelChanges
        entityChanges = ArrayList(child.entityChanges)
        ownIds = HashSet()
        wrote()
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
                "noChanges(result) in place of changes { }, where the unit of work's family allows it"
        }
        // the flatten above is kept, since nothing clears it once the accumulator is closed
        closed = true
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

    private fun registering(change: ModelChange) {
        check(change.id !in modelChanges) {
            "Change for a given model [${change.id.stringValue()}] was already registered. The block registered " +
                "it twice, and the second registration would replace the first and drop its write. Register a " +
                "model once: make every mutation first, then call add or update with the last instance. A second " +
                "update is accepted only for a model the parent or a composed child registered"
        }
        modelChanges[change.id] = change
        ownIds?.add(change.id)
        wrote()
    }

    /**
     * A block's changes as it handed them to a composed child: the block, and the writes it had made then. It is
     * read-only, so nothing can write into the parent through it. The child's block copies the parent's changes
     * when it starts. A parent that wrote after the hand-off, from the factory or the params lambda, would leak that
     * write into the copy, so the block refuses to start; [merging] refuses a child whose parent wrote while it ran.
     */
    internal class HandOff internal constructor(
        private val parent: ChangesAccumulator,
        private val writes: Int,
    ) {
        internal fun isFrom(accumulator: ChangesAccumulator) = parent === accumulator && writes == accumulator.writes

        /**
         * Asks the parent as it is now, for a child's `noChanges`. A write the parent made after the hand-off
         * counts: the child adds nothing, so the parent persists that write itself.
         */
        internal fun persistsWritesOf(model: Model<*, *>): Boolean = parent.persistsWritesOf(model)

        // the child's own copy of the parent's changes, every id inherited
        internal fun start(uowName: String): ChangesAccumulator {
            check(writes == parent.writes) { notStartedFromParent(uowName) }
            return ChangesAccumulator(LinkedHashMap(parent.modelChanges), ArrayList(parent.entityChanges), this)
        }
    }

    companion object {
        /**
         * The accumulator a composed child's block starts from: its own copy of the parent's changes. A block
         * that runs on its own, with no parent, starts empty.
         */
        internal fun seededFrom(handOff: HandOff?, uowName: String): ChangesAccumulator =
            handOff?.start(uowName) ?: ChangesAccumulator()

        private fun notStartedFromParent(uowName: String) =
            // an anonymous UoW has an empty simple name
            "Composed ${uowName.ifEmpty { "child UnitOfWork" }} did not start from the changes its parent holds " +
                "now; a child extends composable.UnitOfWork or a WrappingUnitOfWork family and not the plain " +
                "UnitOfWork, it is constructed with the ExecutionContext given to the factory, and its parent " +
                "does not register from the params lambda or while the child runs"
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
