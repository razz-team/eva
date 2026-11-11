package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model
import com.razz.eva.domain.Principal
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.WrappingUnitOfWork
import com.razz.eva.uow.composable.ChangesDsl
import com.razz.eva.uow.composable.NotComposableUow
import kotlin.reflect.KClass

/**
 * A unit of work that may write a guarded model only when its params carry a proof for it. The application says which
 * models are guarded ([Guarding]) and issues the proofs; [PF] is its proof type and [P] the principal it runs under.
 * Declare each guarded model the unit of work writes as a [Proven] field of [PARAMS]. Write the body as in every unit
 * of work: `override suspend fun tryPerform(principal, params) = changes { ... }`. In that `changes` block
 * `params.x.model()` returns the model, and the block must call `update` or `notChanged` on it, so the write fails if
 * the stored model changed after the proof was issued. A block registers at least one change: one that registers none,
 * for example over an empty `List<Proven>`, fails with "No changes to persist". Build the unit of work with the
 * [ExecutionContext] its factory receives: that context carries the attempt, and the block reads only the [Proven]
 * fields of params built in that attempt. A guarded unit of work never runs as a child: it implements
 * [NotComposableUow], so passing it to `execute` inside a block does not compile.
 */
abstract class GuardedUnitOfWork<P, PF, PARAMS, RESULT>(
    private val executionContext: ExecutionContext,
    configuration: Configuration,
    /** The model types that need a proof. One object for the whole application. */
    private val guarding: Guarding,
    /**
     * The model types the block may `update` without a proof: models the application does not guard yet. The
     * constructor throws if a model of one of them can be guarded.
     */
    val unguardedWrites: Set<KClass<out Model<*, *>>>,
) : WrappingUnitOfWork<P, PARAMS, RESULT, GuardedChangesDsl<PF>>(executionContext, configuration),
    NotComposableUow
    where P : Principal<*>,
          PF : Proof<*, *, *>,
          PARAMS : UowParams<PARAMS>,
          RESULT : Any {

    init {
        val guarded = unguardedWrites.filter(guarding::canBeGuarded)
        require(guarded.isEmpty()) {
            "${label()} lists ${guarded.map { it.simpleName }} in unguardedWrites, and a model of each can be " +
                "guarded. Remove them, and write such models through Proven fields"
        }
    }

    /**
     * The body of the unit of work. Write it as `= changes { ... }`: nothing else creates its [Block], so
     * `noChanges()`, changes built by hand and the block of another unit of work are refused.
     */
    abstract override suspend fun tryPerform(principal: P, params: PARAMS): Block

    // onFailure gets the models the failed attempt read. It loads nothing again: no proof would allow a new read.
    final override suspend fun onFailure(params: PARAMS, ex: PersistenceException): RESULT =
        onFailure(FailedAttempt(label()), params, ex)

    /**
     * Called as [BaseUnitOfWork.onFailure] is, when the write failed and no retry is left. `attempt[params.x]`
     * returns the model the failed attempt read from that field. The default rethrows [ex].
     */
    open suspend fun onFailure(attempt: FailedAttempt, params: PARAMS, ex: PersistenceException): RESULT = throw ex

    // The guarded DSL wraps the block's composable ChangesDsl, so a child run with execute merges as in any
    // composable unit of work.
    final override fun wrap(dsl: ChangesDsl): GuardedChangesDsl<PF> =
        GuardedChangesDsl(dsl, label(), unguardedWrites, guarding, executionContext.attempt)

    final override fun finish(dsl: GuardedChangesDsl<PF>, result: RESULT) {
        val unwritten = dsl.readNotWritten()
        check(unwritten.isEmpty()) {
            "${label()} read ${unwritten.map { it.stringValue() }} with model() and registered no change for " +
                "them. Call update(model) on each, or notChanged(model) to keep it as it is"
        }
    }

    private fun label() = name().ifEmpty { "A guarded unit of work" }
}
