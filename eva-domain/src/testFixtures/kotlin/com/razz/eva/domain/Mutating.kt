package com.razz.eva.domain

/**
 * The fixtures' witness mint: runs [block] with a [Witness] for the receiver's id type in context, so a
 * fixture or a spec can call witnessed mutators outside any change block. Lives in the test-fixtures
 * artifact; production code reaches it only by depending on that artifact from a main source set.
 */
fun <MID, E, M, R> M.mutating(block: context(Witness<MID>) M.() -> R): R
    where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
    context(Witness<MID>()) { this.block() }

/** The universal fixture witness, for a fixture that mutates models of several id types at once. */
fun <R> mutating(block: context(Witness<ModelId<out Comparable<*>>>) () -> R): R =
    context(Witness<ModelId<out Comparable<*>>>()) { block() }
