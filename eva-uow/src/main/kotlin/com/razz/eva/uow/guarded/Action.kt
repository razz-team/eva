package com.razz.eva.uow.guarded

/**
 * One operation a [GuardedUnitOfWork] runs on its target, named for the audit row. A framework declares its own
 * actions on this, and binds each guarded unit of work to the type of the one it runs, so a [Proof] for another
 * action does not compile against its [Guarded] params.
 */
interface Action {
    val name: String
}
