package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Witness
import com.razz.eva.domain.Principal
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.PersistedLookup
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.modelsIn
import com.razz.eva.uow.composable.ChangesDsl
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.reflect.KClass

/**
 * The receiver of a [GuardedUnitOfWork]'s `changes { }` block. It works as the composable [ChangesDsl] does, with these
 * limits. `update` accepts a model the block read from a [Proven] with [model], at the version it had when read, and
 * a model whose type the unit of work lists in `unguardedWrites`; it refuses every other model. `add` accepts every
 * new model. `update(model) { }` and `add { }` run their lambda with a [Witness], as the composable DSL's do, and check
 * the model as `update` and `add` do. A model an aggregate owns is checked as if the block registered it itself.
 * [execute] runs a child unit of work and refuses a child that registers a model of a guarded type ([Guarding]). It has
 * no entity functions: a child registers entities. [PF] is the proof type whose [Proven] fields the block can read.
 */
class GuardedChangesDsl<PF : Proof<*, *, *>> internal constructor(
    private val dsl: ChangesDsl,
    private val uowName: String,
    private val unguarded: Set<KClass<out Model<*, *>>>,
    private val guarding: Guarding,
    private val attempt: InstantiationContext?,
) {

    // Each attempt runs a new block, so it starts empty on a retry.
    private val read = mutableMapOf<ModelId<*>, Proven<*, *>>()

    /** The ids of the models the block read with [model] and its changes do not hold, directly or in an aggregate. */
    internal fun readNotWritten(): List<ModelId<*>> {
        val written = dsl.changes.flattenChildModels().mapTo(mutableSetOf()) { it.id }
        return (read.keys - written).toList()
    }

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        checkOwnedModels(model)
        return dsl.add(model)
    }

    /** Adds the model [create] returns, as `add(model)` does. [create] runs with a [Witness] for its id type. */
    fun <MID, E, M> add(create: context(Witness<MID>) () -> M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        dsl.add { create().also { checkOwnedModels(it) } }

    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        checkMayUpdate(model)
        checkOwnedModels(model)
        dsl.update(model)
        return model
    }

    /**
     * Runs [mutate] on [model] with a [Witness] for its id type and registers the model it returns, as the composable
     * `update(model) { }` does. `update` must accept [model]; if not, this throws before [mutate] runs. The model
     * [mutate] returns is checked as `update` checks it. If [mutate] returns [model] itself, nothing changed: a stored
     * model is kept as `notChanged` keeps it.
     */
    fun <MID, E, M, R> update(model: M, mutate: context(Witness<MID>) M.() -> R): R
        where M : Model<MID, E>, R : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        checkMayUpdate(model)
        return dsl.update(model) {
            mutate().also { mutated ->
                checkMayUpdate(mutated)
                checkOwnedModels(mutated)
            }
        }
    }

    // update accepts a model the block read with model(), at the version model() returned, or one of an unguarded
    // type.
    private fun checkMayUpdate(model: Model<*, *>) {
        val wasRead = checkReadVersion(model)
        check(wasRead || unguarded.any { it.isInstance(model) }) {
            val remedy = if (guarding.canBeGuarded(model::class)) {
                "Read it with params.x.model()"
            } else {
                "Read it with params.x.model(), or list its type in unguardedWrites"
            }
            "$uowName may not update ${model.id().stringValue()}: the block did not read it from a Proven with " +
                "model(), and ${model::class.simpleName} is not in unguardedWrites. $remedy"
        }
    }

    /**
     * Returns the model this attempt may write. On the first attempt it is the model the proof was issued for. On a
     * retry it is the model the reload returns, after the proof allowed the action on it again; a refusal throws
     * here. After this call `update` accepts the model, in this block only. The block must call `update` or
     * `notChanged` on it, or the block fails. The unit of work knows its attempt from the [ExecutionContext] it was
     * built with. A [Proven] from params built in another attempt or another run, or that another block has read, is
     * refused: build the params anew for each attempt. So is a [Proven] of an attempt that has ended. A unit of work
     * built with a context that carries no attempt, as a composed child is, reads no [Proven].
     */
    suspend fun <M : Model<*, *>> Proven<PF, M>.model(): M {
        check(attempt != null) {
            "$uowName cannot read the Proven for $targetId: it was built with an ExecutionContext that carries no " +
                "attempt. Build the unit of work in the factory of the execute call, with the ExecutionContext the " +
                "factory receives. A composed child reads no Proven. In a spec, build it with " +
                "executionContext.forAttempt(attempt)"
        }
        check(belongsTo(attempt)) {
            "$uowName cannot read the Proven for $targetId: its params were built in another attempt or another " +
                "run. Build them in the params lambda of the execute call that runs this unit of work. In a " +
                "spec, build the unit of work with executionContext.forAttempt(attempt), with the context that " +
                "built the params"
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
        checkReadVersion(model)
        checkOwnedModels(model)
        dsl.notChanged(model)
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
              UOW : com.razz.eva.uow.composable.UnitOfWork<PRINCIPAL, PARAMS, RESULT> =
        dsl.execute(uowFactory, principal, params) { childName, childChanges ->
            val refused = registeredByChild(childChanges)
            check(refused.isEmpty()) {
                "$uowName refuses composed unit of work [$childName]: it registered " +
                    "${refused.map { it.id().stringValue() }}, models that need a proof. A child may not add, " +
                    "update or keep one with notChanged: write it in $uowName, through a Proven field"
            }
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
    // with model() at that version. A new one is allowed, as add allows every new model. modelsIn lists [model] first,
    // then the models it owns.
    private fun checkOwnedModels(model: Model<*, *>) {
        modelsIn(model).drop(1)
            .filter { it.isDirty() && !it.isNew() && guarding.canBeGuarded(it::class) }
            .forEach { owned ->
                check(checkReadVersion(owned)) {
                    "$uowName registers ${model.id().stringValue()}, which owns the changed model " +
                        "${owned.id().stringValue()}, and the block did not read that model with model(). Read it " +
                        "through its Proven field, and put the model that model() returned into the aggregate"
                }
            }
    }

    // The guarded models the child registered itself: those that are not the same instances the block holds when the
    // child returns. Models an aggregate owns count as the block's. A Changes holds one change per model id, so
    // the filter cannot hide a later change of the same id.
    private fun registeredByChild(childChanges: Changes<*>): List<Model<*, *>> {
        val parents = Collections.newSetFromMap(IdentityHashMap<Model<*, *>, Boolean>())
        dsl.changes.flattenChildModels().mapTo(parents) { it.model }
        return childChanges.modelChangesToPersist
            .filter { guarding.canBeGuarded(it.model::class) }
            .map { it.model }
            .filter { it !in parents }
    }
}
