package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model
import com.razz.eva.domain.Principal
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.Changes
import com.razz.eva.uow.EntityChange
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.ModelChange
import com.razz.eva.uow.PersistedLookup
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.composable.ChangesDsl
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlinx.coroutines.withContext

/**
 * A unit of work that runs only on a proof of type [PF], the one its framework's decider mints, for the principal it
 * runs under, and decided again on the target each attempt loads. It is written like every other unit of work:
 * `override suspend fun perform(principal, params) = changes { ... }`. The block has the target
 * ([GuardedChangesDsl.target]) and must write it, so the target's version check makes the decision hold at commit.
 *
 * It reaches the executor through its own params type, [Guarded], built per attempt in the caller's params block; the
 * proof never sits in the unit of work's own params. The attempt's target reaches `changes { }` through the coroutine
 * context the kernel's `tryPerform` installs around the body, so the instance holds no attempt state.
 */
abstract class GuardedUnitOfWork<P, PF, PARAMS, M, RESULT>(
    private val executionContext: ExecutionContext,
    configuration: Configuration,
    private val guarding: Guarding,
    /**
     * The models the block updates as a plain unit of work does, without a proof: aggregates with no actions yet. A
     * type [guarding] covers is refused here, so its models take a proof.
     */
    val unguardedWrites: Set<KClass<out Model<*, *>>>,
) : BaseUnitOfWork<P, Guarded<PF, PARAMS, M>, RESULT, GuardedChangesDsl<P, M>>(executionContext, configuration)
    where P : Principal<*>,
          PF : Proof<*, *, M>,
          PARAMS : UowParams<PARAMS>,
          M : Model<*, *>,
          RESULT : Any {

    init {
        val covered = unguardedWrites.filter(guarding::covers)
        require(covered.isEmpty()) {
            "${name()} writes ${covered.joinToString { it.simpleName.orEmpty() }} without a proof, " +
                "and that reaches a guarded model"
        }
    }

    final override suspend fun tryPerform(principal: P, params: Guarded<PF, PARAMS, M>): GuardedChanges<RESULT> {
        check(params.proof.principal == principal) { "${name()} was handed a proof minted for another principal" }
        val attempt = Attempt(principal, params.proof.action, params.target())
        return withContext(attempt) { perform(principal, params.params) }
    }

    /**
     * The unit of work's own body: `= changes { ... }`, as every unit of work's `tryPerform`. Only its `changes { }`
     * builds what it returns. Named apart from [tryPerform] because both erase to `(Principal, UowParams)` on the JVM
     * while [P] is a type parameter; a framework that fixes [P] may declare `tryPerform(principal, params)` again and
     * forward it here.
     */
    abstract suspend fun perform(principal: P, params: PARAMS): GuardedChanges<RESULT>

    // Answered on the target the attempt decided, never on a reload the failure path would make.
    final override suspend fun onFailure(params: Guarded<PF, PARAMS, M>, ex: PersistenceException): RESULT {
        val target = checkNotNull(params.decidedOrNull()) {
            "${name()}: a failed write is answered only after its attempt ran"
        }
        return onFailure(target, params.params, ex)
    }

    /** As [BaseUnitOfWork.onFailure], handed the target the failed attempt ran on, which it may answer as it is. */
    open suspend fun onFailure(target: M, params: PARAMS, ex: PersistenceException): RESULT = throw ex

    // The block runs over the composable DSL, so a child it executes merges by its rules, behind the guarded gates.
    final override suspend fun changes(init: suspend GuardedChangesDsl<P, M>.() -> RESULT): GuardedChanges<RESULT> {
        val attempt = checkNotNull(coroutineContext[attemptKey]) {
            "${name()}: changes { } runs only inside tryPerform"
        }
        return Produced(
            ChangesDsl.changes(executionContext) {
                val dsl = GuardedChangesDsl(this, attempt.target, attempt.principal, unguardedWrites, guarding)
                val result = dsl.init()
                check(dsl.wroteTarget) {
                    "${name()} runs ${attempt.action.name} without writing ${attempt.target.id().stringValue()}"
                }
                result
            },
        )
    }

    // One key per instance: a guarded unit of work composed under another finds its own attempt, not the parent's.
    private val attemptKey = object : CoroutineContext.Key<Attempt> {}

    private inner class Attempt(
        val principal: P,
        val action: Action,
        val target: M,
    ) : AbstractCoroutineContextElement(attemptKey)

    private class Produced<R>(private val changes: Changes<R>) : GuardedChanges<R>() {
        override val result: R get() = changes.result
        override val modelChangesToPersist: List<ModelChange> get() = changes.modelChangesToPersist
        override val entityChangesToPersist: List<EntityChange> get() = changes.entityChangesToPersist
        override val resultBuilder: ((PersistedLookup) -> Any?)? get() = changes.resultBuilder
    }
}
