package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model
import com.razz.eva.domain.Principal

/**
 * Evidence, minted by the framework's own decider, that [principal] may do [action] to [target] as it was when
 * decided. A [GuardedUnitOfWork] runs on one through its [Guarded] params; a block writes one more model under one
 * through a [Proven]. The kernel asks a proof three things: to be spent on the run it binds ([run]), to decide
 * again on the model a retry loads ([decideAgain]), and to be spent on the one write it covers ([lend]). How a
 * decision is made, reported or refused is the minter's.
 */
interface Proof<out P : Principal<*>, out A : Action, M : Model<*, *>> {

    val principal: P

    val action: A

    /** The model the decision was about, as stored: what the first attempt writes. */
    val target: M

    /** Decides [action] for [principal] again on [reloaded], the target as a retry loaded it. Refuses by throwing. */
    suspend fun decideAgain(reloaded: M)

    /** Spends the proof on the unit of work it runs, once: called when the first attempt's params bind it. */
    fun run()

    /** Spends the proof on a write in another unit of work's block. Lent, it runs nothing; run, it is not lent. */
    fun lend()

    /** Whether an attempt lent this proof: only then may a retry decide it again on a reload. */
    val lent: Boolean
}
