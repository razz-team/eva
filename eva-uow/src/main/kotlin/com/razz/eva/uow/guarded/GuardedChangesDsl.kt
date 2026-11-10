package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.ModelChange
import com.razz.eva.uow.PersistedLookup
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.UpdateModel
import com.razz.eva.uow.composable.ChangesDsl
import com.razz.eva.uow.composable.ChangesDsl.ChildRun
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.reflect.KClass

/**
 * The receiver of a [GuardedUnitOfWork]'s `changes { }` block. It works as the composable [ChangesDsl] does, with these
 * limits. `update` accepts a model the block read from a [Proven] with [model], at the version it had when read, and
 * a model whose type the unit of work lists in `unguardedWrites`; it refuses every other model. `add` accepts every
 * new model. A model an aggregate owns is checked as if the block registered it itself. [execute] runs a child unit
 * of work and refuses a child that registers a model of a guarded type ([Guarding]). It has no entity functions: a
 * child registers entities. [PF] is the proof type whose [Proven] fields the block can read.
 */
class GuardedChangesDsl<PF : Proof<*, *, *>> internal constructor(
    private val dsl: ChangesDsl,
    private val uowName: String,
    private val unguarded: Set<KClass<out Model<*, *>>>,
    private val guarding: Guarding,
    private val attempt: InstantiationContext?,
) {

    // Each attempt runs a new block, so both start empty on a retry.
    private val read = mutableMapOf<ModelId<*>, Proven<*, *>>()
    private val written = mutableSetOf<ModelId<*>>()

    /** The ids of the models the block read with [model] and registered with neither `update` nor `notChanged`. */
    internal fun readNotWritten(): List<ModelId<*>> = read.keys.filterNot { it in written }

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        checkOwnedModels(model)
        return dsl.add(model)
    }

    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        val wasRead = checkReadVersion(model)
        check(wasRead || unguarded.any { it.isInstance(model) }) {
            "$uowName may not update ${model.id().stringValue()}: the block did not read it from a Proven with " +
                "model(), and ${model::class.simpleName} is not in unguardedWrites. Read it with params.x.model(), " +
                "or list its type in unguardedWrites"
        }
        checkOwnedModels(model)
        dsl.update(model)
        if (wasRead) written += model.id()
        return model
    }

    /**
     * Returns the model this attempt may write. On the first attempt it is the model the proof was issued for. On a
     * retry it is the model the reload returns, after the proof allowed the action on it again; a refusal throws
     * here. After this call `update` accepts the model, in this block only. The block must call `update` or
     * `notChanged` on it, or the block fails. A [Proven] from params built in another run, or that another block has
     * read, is refused: build the params anew for each attempt.
     */
    suspend fun <M : Model<*, *>> Proven<PF, M>.model(): M {
        check(belongsTo(attempt)) {
            "$uowName cannot read the Proven for $targetId: its params were built in another run, or for another " +
                "principal. Build them in the params lambda of the execute call that runs this unit of work. In a " +
                "spec, call tryPerform inside inAttempt(context), with the context that built the params"
        }
        readOrNull()?.let { earlier ->
            check(read[earlier.id()] === this) {
                "$uowName: another block already read the Proven for ${earlier.id().stringValue()}. Build the " +
                    "params in the params lambda for each attempt, and do not keep a Proven between attempts or runs"
            }
        }
        val model = read()
        val earlier = read.putIfAbsent(model.id(), this)
        check(earlier == null || earlier === this) {
            "$uowName: two Proven fields carry ${model.id().stringValue()}. A block reads a model through one " +
                "proof only: pass it once"
        }
        return model
    }

    fun <MID, E, M> notChanged(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        val wasRead = checkReadVersion(model)
        checkOwnedModels(model)
        dsl.notChanged(model)
        if (wasRead) written += model.id()
        return model
    }

    /**
     * Builds the result as [ChangesDsl.roundtrip] does: first from the models the block and its children registered,
     * then again from the stored models after the write.
     */
    fun <R> roundtrip(build: (p: PersistedLookup) -> R): R = dsl.roundtrip(build)

    /**
     * Runs a child unit of work under [principal] and merges its changes into this block's, as [ChangesDsl.execute]
     * does. The child needs no proof for its own models. It may not register a model of a guarded type with `add`,
     * `update` or `notChanged`: this block throws before it merges any of the child's changes. Write such a model in
     * this block, through a [Proven].
     */
    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        uowFactory: (ExecutionContext) -> UOW,
        principal: PRINCIPAL,
        params: InstantiationContext.Internal.() -> PARAMS,
    ): RESULT
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : com.razz.eva.uow.composable.UnitOfWork<PRINCIPAL, PARAMS, RESULT> {
        val run = dsl.runChild(uowFactory, principal, params)
        // A Changes holds one change per model id, so the filter cannot hide a later change of the same id.
        val guarded = run.changes.modelChangesToPersist.filter { guarding.canBeGuarded(it.model::class) }
        if (guarded.isEmpty()) return dsl.merge(run)
        val refused = run.registeredByChild(guarded)
        check(refused.isEmpty()) {
            "$uowName refuses composed unit of work [${run.uowName}]: it registered " +
                "${refused.map { it.id().stringValue() }}, models that need a proof. A child may not add, update or " +
                "keep one with notChanged: write it in $uowName, through a Proven field"
        }
        return dsl.merge(run)
    }

    // The block writes the model at the version it read, so the optimistic lock fails the write if the stored model
    // changed after the proof allowed it.
    private fun checkReadVersion(model: Model<*, *>): Boolean {
        val readModel = read[model.id()]?.readOrNull() ?: return false
        check(model.version() == readModel.version()) {
            "$uowName registers ${model.id().stringValue()} at version ${model.version().version}, and model() " +
                "returned it at version ${readModel.version().version}. Change the model that model() returned"
        }
        return true
    }

    // Registering an aggregate registers the models it owns. A changed one of a guarded type must have been read
    // with model() at that version, and then counts as written. A new one is allowed, as add allows every new model.
    private fun checkOwnedModels(model: Model<*, *>) {
        ChangesAccumulator.ownedChangesOf(model)
            .filter { it is UpdateModel<*, *, *> && guarding.canBeGuarded(it.model::class) }
            .forEach { owned ->
                check(checkReadVersion(owned.model)) {
                    "$uowName registers ${model.id().stringValue()}, which owns the changed model " +
                        "${owned.id.stringValue()}, and the block did not read that model with model(). Read it " +
                        "through its Proven field, and put the model that model() returned into the aggregate"
                }
                written += owned.id
            }
    }

    // The guarded models the child registered itself: those that are not the same instances the block had registered
    // before the child ran. Models an aggregate owns count as the block's. Per id the last change, as the merge keeps.
    private fun ChildRun<*>.registeredByChild(guarded: List<ModelChange>): List<Model<*, *>> {
        val parents = Collections.newSetFromMap(IdentityHashMap<Model<*, *>, Boolean>())
        parentChanges.flattenChildModels().mapTo(parents) { it.model }
        return guarded.associateBy { it.id }.values.map { it.model }.filter { it !in parents }
    }
}
