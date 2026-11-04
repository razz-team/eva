package com.razz.eva.uow.composable

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.domain.TestModel.ActiveTestModel
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UowParams

/**
 * A unit of work with a DSL of its own, built on [BaseUnitOfWork] and wrapping the composable [ChangesDsl]
 * through [ChangesDsl.changes]: what a framework outside eva writes to gate the block's writes while its
 * children still merge by eva's rules.
 */
internal abstract class FencedUow<R : Any>(
    private val executionContext: ExecutionContext,
) : BaseUnitOfWork<TestPrincipal, DummyUow.Params, R, FencedDsl>(executionContext) {

    final override suspend fun changes(init: suspend FencedDsl.() -> R): Changes<R> =
        ChangesDsl.changes(executionContext) { FencedDsl(this).init() }
}

/** The wrapper: registrations go to the composable DSL, and a child that writes an [ActiveTestModel] is refused. */
internal class FencedDsl(private val dsl: ChangesDsl) {

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> = dsl.add(model)

    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> = dsl.update(model)

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        uowFactory: (ExecutionContext) -> UOW,
        principal: PRINCIPAL,
        params: InstantiationContext.Internal.() -> PARAMS,
    ): RESULT
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : UnitOfWork<PRINCIPAL, PARAMS, RESULT> {
        val before = dsl.registered()
        val result = dsl.execute(uowFactory, principal, params)
        val fenced = dsl.registered().filter { model -> before.none { it === model } && model is ActiveTestModel }
        check(fenced.isEmpty()) {
            "A child wrote ${fenced.map { it.id().stringValue() }}, which only this block may write"
        }
        return result
    }
}
