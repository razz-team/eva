package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.ModelChange
import com.razz.eva.uow.PersistedLookup
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.composable.ChangesDsl
import com.razz.eva.uow.composable.ChangesDsl.ChildRun
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.reflect.KClass

/**
 * The `changes { }` of a [GuardedUnitOfWork]: the composable DSL behind the gates a proof of type [PF] puts on it.
 * It updates a model a [Proven] decided in this block (read with [model]), at the version it was decided on, and a
 * model the unit of work declares in `unguardedWrites`; new models are added as anywhere. [execute] composes a child
 * as every unit of work does, and refuses one that writes a model the block's [Guarding] covers.
 */
class GuardedChangesDsl<PF : Proof<*, *, *>> internal constructor(
    private val dsl: ChangesDsl,
    private val unguarded: Set<KClass<out Model<*, *>>>,
    private val guarding: Guarding,
) {

    // A fresh block runs each attempt, so a retry decides each model again.
    private val decided = mutableMapOf<ModelId<*>, Proven<*, *>>()
    private val held = mutableSetOf<ModelId<*>>()

    /** The ids the block decided and neither updated nor held with `notChanged`. */
    internal fun decidedNotHeld(): List<ModelId<*>> = decided.keys.filterNot { it in held }

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> = dsl.add(model)

    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        val isDecided = checkDecidedVersion(model)
        check(isDecided || unguarded.any { it.isInstance(model) }) {
            "A guarded unit of work updates only a model a proof decided in its block or a declared type, " +
                "and ${model.id().stringValue()} is neither"
        }
        dsl.update(model)
        if (isDecided) held += model.id()
        return model
    }

    /**
     * The model this attempt writes under the proof, decided: the proof's own on the first attempt, the reload's on
     * a retry. `update` then takes it, in this block only, and the block must update it or hold it with `notChanged`.
     */
    suspend fun <M : Model<*, *>> Proven<PF, M>.model(): M {
        val model = decided()
        val earlier = decided.putIfAbsent(model.id(), this)
        check(earlier == null || earlier === this) {
            "A block decides ${model.id().stringValue()} once, and two proofs decided it"
        }
        return model
    }

    fun <MID, E, M> notChanged(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        val isDecided = checkDecidedVersion(model)
        dsl.notChanged(model)
        if (isDecided) held += model.id()
        return model
    }

    /** As the composable DSL's: the result over the block's models, children's included, and over the stored set. */
    fun <R> roundtrip(build: (p: PersistedLookup) -> R): R = dsl.roundtrip(build)

    /**
     * Runs a child under [principal] as the composable DSL does, its changes merged into this block's by its rules.
     * The child writes undecided, except a model the block's [Guarding] covers: that write takes a proof, so a child
     * that adds, updates, merges onto or `notChanged`s one is refused before its changes are taken.
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
        // Filtering before the last-per-id step is exact: a Changes holds one change per id.
        val covered = run.changes.modelChangesToPersist.filter { guarding.covers(it.model::class) }
        if (covered.isEmpty()) return dsl.take(run)
        val refused = run.modelsNotHeldBySeed(covered)
        check(refused.isEmpty()) {
            "Composed unit of work [${run.uowName}] wrote ${refused.map { it.id().stringValue() }}, " +
                "which its parent refuses"
        }
        return dsl.take(run)
    }

    // A decided model is written at the version it was decided on: the version check then holds the decision.
    private fun checkDecidedVersion(model: Model<*, *>): Boolean {
        val decision = decided[model.id()]?.decidedOrNull() ?: return false
        check(model.version() == decision.version()) {
            "${model.id().stringValue()} was decided at version ${decision.version().version}, " +
                "and the block writes it at ${model.version().version}"
        }
        return true
    }

    // By instance: a model registered since, or again on top of the seed's; the seed's owned models count as held.
    // Per id the last change, as the merge keeps it.
    private fun ChildRun<*>.modelsNotHeldBySeed(covered: List<ModelChange>): List<Model<*, *>> {
        val held = Collections.newSetFromMap(IdentityHashMap<Model<*, *>, Boolean>())
        seed.flattenChildModels().mapTo(held) { it.model }
        return covered.associateBy { it.id }.values.map { it.model }.filter { it !in held }
    }
}
