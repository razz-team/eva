package com.razz.eva.uow

/**
 * Evidence that a value went through the changes DSL. `changes { }` persists only what was handed to
 * `add` / `update` / `notChanged`; a freshly mutated model returned without one of those calls compiles,
 * "succeeds", and writes nothing. A [RegisteringUnitOfWork] (plain or composable) asks its block for a
 * `Registered<RESULT>`, and the registering DSLs are the only things that mint one, so the
 * forgotten-registration branch stops compiling instead of silently dropping the write.
 *
 * The constructor is internal: domain code cannot forge evidence, it can only earn it
 * (or state the exception in the open, via [resultOnly]).
 */
class Registered<out R> internal constructor(
    /** The registered value, for the rare block that needs to keep using it after registering it. */
    val result: R,
) {

    /**
     * Shapes the registered result (an id, a projection, a response pair). The receiver was registered;
     * mapping to a *different, dirty* model would forge evidence, so keep transforms to projections of
     * what was registered.
     */
    fun <T> map(transform: (R) -> T): Registered<T> = Registered(transform(result))

    infix fun <T> with(other: Registered<T>): Registered<Pair<R, T>> = Registered(result to other.result)
}

/**
 * The stated exception: a result that is not a model, such as a computed value, a report, or a projection
 * built before registration. Spelling it at the return site is the point; a reviewer sees the claim
 * "nothing here needed registering" instead of an absence.
 */
fun <R> resultOnly(result: R): Registered<R> = Registered(result)
