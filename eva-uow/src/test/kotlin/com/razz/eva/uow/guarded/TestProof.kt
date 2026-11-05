package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model
import com.razz.eva.domain.Principal
import java.util.concurrent.atomic.AtomicReference

/**
 * A proof as a framework's decider mints one: decided on [target] for [principal], deciding again through [decide]
 * on a reload, spent once. [decidedAgain] records every reload it was asked about.
 */
internal class TestProof<M : Model<*, *>>(
    override val principal: Principal<*>,
    override val target: M,
    private val decide: (M) -> Unit,
) : Proof<Principal<*>, Write, M> {

    override val action = Write

    val decidedAgain = mutableListOf<M>()

    private enum class Use { RAN, LENT }

    private val use = AtomicReference<Use?>(null)

    override suspend fun decideAgain(reloaded: M) {
        decidedAgain += reloaded
        decide(reloaded)
    }

    override fun run() =
        check(use.compareAndSet(null, Use.RAN)) { "A proof for ${action.name} runs once, and this one ran" }

    override fun lend() =
        check(use.compareAndSet(null, Use.LENT)) { "A proof for ${action.name} covers one write, and this one ran" }

    override val lent: Boolean get() = use.get() == Use.LENT
}
