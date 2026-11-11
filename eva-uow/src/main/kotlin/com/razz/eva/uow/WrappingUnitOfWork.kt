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
 * `abstract override suspend fun tryPerform(principal: PRINCIPAL, params: PARAMS): Block<RESULT>`. Then
 * `noChanges()` and any other [Changes] do not compile there. A family that keeps `noChanges(result)` leaves the
 * return type as it is. This base does not run as a composed child: for that, extend
 * [com.razz.eva.uow.composable.WrappingUnitOfWork].
 */
abstract class WrappingUnitOfWork<PRINCIPAL, PARAMS, RESULT, C : Any>(
    private val executionContext: ExecutionContext,
    configuration: Configuration = default(),
) : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, C>(executionContext, configuration)
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    /** Wraps the block's DSL in the family's own. Runs once for each `changes { }` block, before the block. */
    protected abstract fun wrap(dsl: ChangesDsl): C

    /**
     * Runs after the block returns [result] and before its changes are built. Throw to refuse the block: nothing
     * it registered is written. The default accepts every block.
     */
    protected open fun finish(dsl: C, result: RESULT) {}

    protected suspend fun changes(init: suspend C.() -> RESULT): Block<RESULT> =
        Block(
            ChangesDsl.changes(executionContext) {
                val family = wrap(this)
                val result = family.init()
                finish(family, result)
                result
            },
        )

    /** The changes a `changes { }` block of a [WrappingUnitOfWork] built. Nothing else creates one. */
    class Block<R> internal constructor(private val changes: Changes<R>) : Changes<R>() {
        override val result: R get() = changes.result
        override val modelChangesToPersist: List<ModelChange> get() = changes.modelChangesToPersist
        override val entityChangesToPersist: List<EntityChange> get() = changes.entityChangesToPersist
        override val resultBuilder: ((PersistedLookup) -> Any?)? get() = changes.resultBuilder
        override val stubbed: Boolean get() = changes.stubbed
    }
}
