package com.razz.eva.uow.guarded

/**
 * What a [Proof] allows a principal to do to a model, for example move an employee. [name] is written to the audit
 * row. The application declares its actions as implementations of this interface. A params field declares the action
 * type it accepts, so a [Proven] for another action is a compile error there.
 */
interface Action {
    val name: String
}
