package com.razz.eva.uow.composable

/**
 * Evidence that a value's relationship to the change set was accounted for. `changes { }` persists only
 * what was handed to `add` / `update` / `notChanged`; a freshly mutated model returned without one of
 * those calls compiles, "succeeds", and writes nothing. A [ProvingUnitOfWork] asks its block for an
 * `Accounted<RESULT>`, and [ProvingChangesDsl] is the only mint, so the forgotten-registration branch
 * stops compiling instead of silently dropping the write.
 *
 * The guard covers the block's tail, not everything: a registration must be stated
 * (`add` / `update` / `notChanged`) or the exception to it must be stated ([ProvingChangesDsl.noModelResult]).
 * A mutation discarded mid-block, or a secondary model never registered at all, still compiles;
 * [ProvingUnitOfWork] backs the tail check with runtime verification that no new or dirty model leaves
 * the block unregistered.
 *
 * Evidence is bound to the block that minted it: the constructor is internal and requires the minting
 * DSL, and [ProvingUnitOfWork] rejects an `Accounted` minted by another block's DSL instance.
 */
class Accounted<out R> internal constructor(
    /** The accounted value, for the rare block that needs to keep using it after registering it. */
    val result: R,
    internal val origin: ProvingChangesDsl,
) {

    /**
     * Shapes the accounted result into a projection (an id, a DTO, a response). A new or dirty model
     * that was never registered cannot hide in the projection: the models reachable from the block's
     * final value are verified against the change set when the block completes.
     */
    fun <T> map(transform: (R) -> T): Accounted<T> = Accounted(transform(result), origin)
}
