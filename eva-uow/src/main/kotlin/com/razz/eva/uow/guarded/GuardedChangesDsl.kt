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
import com.razz.eva.uow.composable.UnitOfWork
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.reflect.KClass

/**
 * The `changes { }` of a [GuardedUnitOfWork]: the composable DSL behind the gates a proof puts on it. It updates
 * the [target], a model a [Proven] decided in this block (read with [model]), and a model the unit of work declares
 * in `unguardedWrites`; new models are added as anywhere. [execute] composes a child as every unit of work does,
 * and refuses one that writes a model the block's [Guarding] covers. The block itself writes no entities; a child
 * it executes may.
 */
class GuardedChangesDsl<P : Principal<*>, T : Model<*, *>> internal constructor(
    private val dsl: ChangesDsl,
    val target: T,
    private val principal: P,
    private val unguarded: Set<KClass<out Model<*, *>>>,
    private val guarding: Guarding,
) {

    // The ids a Proven decided in this block: a fresh block runs each attempt, so a retry reads each one again.
    private val proven = mutableSetOf<ModelId<*>>()

    internal var wroteTarget = false
        private set

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> = dsl.add(model)

    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        val isTarget = model.id() == target.id()
        check(isTarget || model.id() in proven || unguarded.any { it.isInstance(model) }) {
            "A guarded unit of work updates only its target ${target.id().stringValue()}, " +
                "not ${model.id().stringValue()}, which is neither proven nor declared"
        }
        dsl.update(model)
        if (isTarget) wroteTarget = true
        return model
    }

    /**
     * The model this attempt writes under the proof, decided for this block's principal: the proof's own on the
     * first attempt, the reload's on a retry. `update` then takes it, in this block only.
     */
    suspend fun <PF : Proof<*, *, M>, M : Model<*, *>> Proven<PF, M>.model(): M {
        check(proof.principal == principal) {
            "A proof for ${proof.action.name} on ${proof.target.id().stringValue()} was minted for another principal"
        }
        val model = decided()
        proven += model.id()
        return model
    }

    fun <MID, E, M> notChanged(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> = dsl.notChanged(model)

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
              UOW : UnitOfWork<PRINCIPAL, PARAMS, RESULT> {
        val run = dsl.runChild(uowFactory, principal, params)
        val covered = run.changes.modelChangesToPersist.filter { guarding.covers(it.model::class) }
        if (covered.isEmpty()) return dsl.take(run)
        val refused = run.modelsNotHeldBySeed(covered)
        check(refused.isEmpty()) {
            "Composed unit of work [${run.uowName}] wrote ${refused.map { it.id().stringValue() }}, " +
                "which its parent refuses"
        }
        return dsl.take(run)
    }

    // By instance: a model registered since, or again on top of the seed's; the seed's owned models count as held.
    // Per id the last change, as the merge keeps it.
    private fun ChildRun<*>.modelsNotHeldBySeed(covered: List<ModelChange>): List<Model<*, *>> {
        val held = Collections.newSetFromMap(IdentityHashMap<Model<*, *>, Boolean>())
        seed.flattenChildModels().mapTo(held) { it.model }
        return covered.associateBy { it.id }.values.map { it.model }.filter { it !in held }
    }
}
