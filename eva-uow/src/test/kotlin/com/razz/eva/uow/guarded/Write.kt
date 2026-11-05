package com.razz.eva.uow.guarded

/** The one action the guarded fixtures run. */
internal object Write : Action {
    override val name = "test.write"
}
