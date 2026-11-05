package com.razz.eva.uow.guarded

import com.razz.eva.IdempotencyKey
import com.razz.eva.domain.Model
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.UowParams

/**
 * What a [GuardedUnitOfWork] runs on: the proof, the unit of work's own [params], and its target, built once per
 * attempt in the params block through [guarded]. The target follows the retry rule: the first attempt keeps the model
 * the proof was decided on, a retry loads it again through [reload] and the proof decides again on what it loaded.
 *
 * A params serializer writes it as `{"action": ..., "target": ..., "params": {...}}`, with [action] and [targetId]
 * as the first two; the principal is the row's own. Encode-only, as every params serializer is.
 */
class Guarded<PF, PARAMS, M> private constructor(
    internal val proof: PF,
    val params: PARAMS,
    private val firstAttempt: Boolean,
    private val reload: suspend () -> M,
) : UowParams<Guarded<PF, PARAMS, M>> where PF : Proof<*, *, M>, PARAMS : UowParams<PARAMS>, M : Model<*, *> {

    private var decided: M? = null

    /** The target this attempt runs on, decided once: the proof's own, or the reload the proof decided again. */
    internal suspend fun target(): M {
        decided?.let { return it }
        val model = if (firstAttempt) proof.target else reloaded()
        decided = model
        return model
    }

    /** The target [target] decided for this attempt, or null before the attempt reached it. */
    internal fun decidedOrNull(): M? = decided

    private suspend fun reloaded(): M {
        val loaded = reload()
        check(loaded.id() == proof.target.id()) {
            "The reload answered ${loaded.id().stringValue()} for the proof's ${proof.target.id().stringValue()}"
        }
        proof.decideAgain(loaded)
        return loaded
    }

    val action: String get() = proof.action.name

    val targetId: String get() = proof.target.id().stringValue()

    override val idempotencyKey: IdempotencyKey? get() = params.idempotencyKey

    companion object {

        /**
         * Binds [proof] and [params] to this attempt, and on the first attempt spends the proof on this run; a retry
         * loads the target through [reload]. [PF] is the minter's own proof type: a unit of work declares it, so a
         * proof of another implementation does not compile against its params.
         */
        fun <PF, PARAMS, M> InstantiationContext.guarded(
            proof: PF,
            params: PARAMS,
            reload: suspend () -> M,
        ): Guarded<PF, PARAMS, M> where PF : Proof<*, *, M>, PARAMS : UowParams<PARAMS>, M : Model<*, *> {
            val firstAttempt = attempt == 0
            if (firstAttempt) proof.run()
            return Guarded(proof, params, firstAttempt, reload)
        }
    }
}
