package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model
import com.razz.eva.domain.Principal

/**
 * A proof as an application issues one: for [principal] on [target]. On a retry [decide] checks the reloaded model
 * again. [decidedAgain] records every reload it was asked about.
 */
internal class TestProof<M : Model<*, *>>(
    override val principal: Principal<*>,
    override val target: M,
    private val decide: (M) -> Unit,
) : Proof<Principal<*>, Write, M>() {

    override val action = Write

    val decidedAgain = mutableListOf<M>()

    override suspend fun decideAgain(reloaded: M) {
        decidedAgain += reloaded
        decide(reloaded)
    }
}
