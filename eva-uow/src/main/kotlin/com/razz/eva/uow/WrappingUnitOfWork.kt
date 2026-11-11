package com.razz.eva.uow

import com.razz.eva.domain.Principal
import com.razz.eva.uow.BaseUnitOfWork.Configuration.Companion.default
import com.razz.eva.uow.composable.ChangesDsl

/**
 * The base for a unit of work family with a change block of its own. The family's DSL [C] wraps the composable
 * [ChangesDsl] of the block, so every registration it makes goes through eva's checks, and its units of work
 * still write `= changes { ... }`.
 *
 * A family that wants its block to be the only way to a result declares
 * `abstract override suspend fun tryPerform(principal: PRINCIPAL, params: PARAMS): Block`. Then `noChanges()`,
 * any other [Changes] and the block of a family with other type arguments do not compile there. The executor and
 * a composable parent refuse a block that another unit of work built. A family that keeps `noChanges(result)`
 * leaves the return type as it is.
 *
 * A family whose units of work run as children of a composable parent also implements
 * [com.razz.eva.uow.composable.ComposableUow]. Its block starts from the changes the parent hands in, so what it
 * registers merges into the parent's changes. `finish` then judges the child's block only: the parent can still
 * extend a model the child registered. A family that must never run as a child implements
 * [com.razz.eva.uow.composable.NotComposableUow].
 */
abstract class WrappingUnitOfWork<PRINCIPAL, PARAMS, RESULT, C : Any>(
    private val executionContext: ExecutionContext,
    configuration: Configuration = default(),
) : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, C>(executionContext, configuration)
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    /**
     * Wraps the block's DSL in the family's own. Runs once for each `changes { }` block, before the block.
     * Declare the override final, or a unit of work of the family can replace it and keep the raw DSL.
     */
    protected abstract fun wrap(dsl: ChangesDsl): C

    /**
     * Runs after the block returns [result] and before its changes are built. Throw to refuse the block: nothing
     * it registered is written. The default accepts every block. Declare the override final in the family.
     */
    protected open fun finish(dsl: C, result: RESULT) {}

    protected suspend fun changes(init: suspend C.() -> RESULT): Block =
        Block(
            ChangesDsl.changes(executionContext) {
                val family = wrap(this)
                val result = family.init()
                finish(family, result)
                result
            },
        )

    /**
     * The changes a `changes { }` block of this unit of work built. Nothing else creates one, and the type is
     * bound to the family's type arguments.
     */
    inner class Block internal constructor(private val changes: Changes<RESULT>) : Changes<RESULT>() {
        internal val owner: WrappingUnitOfWork<*, *, *, *> get() = this@WrappingUnitOfWork
        override val result: RESULT get() = changes.result
        override val resultBuilder: ((PersistedLookup) -> Any?)? get() = changes.resultBuilder
        override val accumulator: ChangesAccumulator? get() = changes.accumulator
    }
}

/**
 * A [WrappingUnitOfWork.Block] remembers the unit of work whose `changes { }` built it. A unit that returns the
 * block of another unit skips its own family's DSL and `finish`, so this fails and names both units.
 */
internal fun checkBuiltBy(uow: BaseUnitOfWork<*, *, *, *>, changes: Changes<*>, name: String) {
    if (changes !is WrappingUnitOfWork<*, *, *, *>.Block) return
    check(changes.owner === uow) {
        "${name.ifEmpty { "A unit of work" }} returned the changes { } block of another unit of work " +
            "[${changes.owner.name().ifEmpty { "anonymous" }}]; return the block its own changes { } built"
    }
}
