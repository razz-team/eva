package com.razz.eva.examples.composition.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.composable.ChangesDsl
import com.razz.eva.uow.composable.ComposableUow

/**
 * A family's own change block, built on the [ChangesDsl] that [CustomUnitOfWork.wrap] hands it. Every operation
 * ends in a call to the wrapped DSL, so a block here composes children and merges what they hand back by the
 * same rules as an eva block.
 */
class CustomChangesDsl internal constructor(private val changes: ChangesDsl) {

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        changes.add(model)

    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> =
        changes.update(model)

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        uowFactory: (ExecutionContext) -> UOW,
        principal: PRINCIPAL,
        params: InstantiationContext.Internal.() -> PARAMS,
    ): RESULT
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *>,
              UOW : ComposableUow =
        changes.execute(uowFactory, principal, params)
}
