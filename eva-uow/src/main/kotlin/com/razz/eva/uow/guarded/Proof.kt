package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model
import com.razz.eva.domain.Principal
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Evidence, minted by the framework's own decider, that [principal] may do [action] to [target] as it was when
 * decided. A unit of work's params carry it as a [Proven]. The kernel asks a proof two things: to decide again on
 * the model a retry loads ([decideAgain]), and to be spent, once, when the first attempt's params bind it. How a
 * decision is made, reported or refused is the minter's; spending is the kernel's.
 */
abstract class Proof<out P : Principal<*>, out A : Action, M : Model<*, *>> {

    abstract val principal: P

    abstract val action: A

    /** The model the decision was about, as stored: what the first attempt writes. */
    abstract val target: M

    /** Decides [action] for [principal] again on [reloaded], the target as a retry loaded it. Refuses by throwing. */
    abstract suspend fun decideAgain(reloaded: M)

    private val spent = AtomicBoolean(false)

    internal fun spend() =
        check(spent.compareAndSet(false, true)) { "A proof for ${action.name} is spent once, and this one was" }

    internal val isSpent: Boolean get() = spent.get()
}
