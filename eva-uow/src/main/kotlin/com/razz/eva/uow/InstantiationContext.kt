package com.razz.eva.uow

import com.razz.eva.domain.Principal

sealed class InstantiationContext(internal val attempt: Int) {

    /**
     * The context the executor builds for each attempt. It also holds the principal the unit of work runs under and
     * the run: one `execute` call with all its retries.
     */
    internal class External(attempt: Int, val principal: Principal<*>, val run: Run) : InstantiationContext(attempt)

    class Internal internal constructor(attempt: Int) : InstantiationContext(attempt)

    /** One run of a unit of work, across its attempts. Compared by identity. */
    internal class Run
}
