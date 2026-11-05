package com.razz.eva.uow.composable

import com.razz.eva.domain.TestModel.ActiveTestModel
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal

/**
 * A unit of work with a DSL of its own, built on [BaseUnitOfWork] and wrapping the composable [ChangesDsl]
 * through [ChangesDsl.changes]: what a framework outside eva writes to gate the block's writes while its
 * children still merge by eva's rules. Its block refuses a child that writes an [ActiveTestModel], and returns
 * [FencedChanges], which only that block produces.
 */
internal abstract class FencedUow<R : Any>(
    private val executionContext: ExecutionContext,
) : BaseUnitOfWork<TestPrincipal, DummyUow.Params, R, FencedDsl>(executionContext) {

    abstract override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params): FencedChanges<R>

    final override suspend fun changes(init: suspend FencedDsl.() -> R): FencedChanges<R> = Produced(
        ChangesDsl.changes(executionContext, refusing = { it is ActiveTestModel }) { FencedDsl(this).init() },
    )

    private class Produced<R>(changes: Changes<R>) : FencedChanges<R>(changes)
}
