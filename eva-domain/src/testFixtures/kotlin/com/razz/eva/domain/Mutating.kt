package com.razz.eva.domain

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.EXACTLY_ONCE
import kotlin.contracts.contract

/**
 * The fixtures' witness mint: runs [block] with a [Witness] for the receiver's id type in context, so a
 * fixture or a spec can call witnessed mutators outside any change block. Lives in the test-fixtures
 * artifact; production code reaches it only by depending on that artifact from a main source set.
 */
@OptIn(ExperimentalContracts::class)
fun <MID, E, M, R> M.mutating(block: context(Witness<MID>) M.() -> R): R
    where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
    contract { callsInPlace(block, EXACTLY_ONCE) }
    return context(Witness<MID>()) { this.block() }
}

/** The universal fixture witness, for a fixture that mutates models of several id types at once. */
@OptIn(ExperimentalContracts::class)
fun <R> mutating(block: context(Witness<ModelId<out Comparable<*>>>) () -> R): R {
    contract { callsInPlace(block, EXACTLY_ONCE) }
    return context(Witness<ModelId<out Comparable<*>>>()) { block() }
}
