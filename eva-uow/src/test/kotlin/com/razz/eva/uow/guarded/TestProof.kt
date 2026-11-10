package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model
import com.razz.eva.domain.Principal

/**
 * A proof as an application issues one: for [principal] on [target]. On a retry [recheck] checks the reloaded model
 * again. [checkedAgain] records every reload it was asked about.
 */
internal class TestProof<M : Model<*, *>>(
    override val principal: Principal<*>,
    override val target: M,
    private val recheck: (M) -> Unit,
) : Proof<Principal<*>, Write, M>() {

    override val action = Write

    val checkedAgain = mutableListOf<M>()

    override suspend fun checkAgain(reloaded: M) {
        checkedAgain += reloaded
        recheck(reloaded)
    }
}
