package com.razz.eva.uow.guarded

/**
 * One operation a [Proof] covers on its target, named for the audit row. A framework declares its own actions on
 * this, and a params field names the type of the one it takes, so a [Proven] for another action does not compile
 * against it.
 */
interface Action {
    val name: String
}
