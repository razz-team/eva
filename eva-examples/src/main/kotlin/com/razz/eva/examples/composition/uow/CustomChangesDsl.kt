package com.razz.eva.examples.composition.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesWithoutResult
import com.razz.eva.uow.UowParams

class CustomChangesDsl internal constructor(private var changes: ChangesWithoutResult) {

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        changes = changes.withAdded(model)
        return model
    }

    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        changes = changes.withUpdated(model)
        return model
    }

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> UOW.tryPerform(
        principal: PRINCIPAL,
        params: PARAMS,
    ): Changes<RESULT>
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : CustomUnitOfWork<PRINCIPAL, PARAMS, RESULT> {
        return this@tryPerform.tryPerform(principal, params)
    }

    infix fun <R> merge(other: Changes<R>): Changes<R> {
        changes = changes.withMerged(other)
        return other
    }

    fun <PRINCIPAL, PARAMS, RESULT, UOW> merge(uow: UOW): UOW
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : CustomUnitOfWork<PRINCIPAL, PARAMS, RESULT> {
        uow.tryPerform()
    }

    companion object {
        internal suspend inline fun <R> changes(
            changes: ChangesWithoutResult,
            @Suppress("REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE")
            init: suspend CustomChangesDsl.() -> R
        ): Changes<R> {
            val dsl = CustomChangesDsl(changes)
            val res = init(dsl)
            return dsl.changes.withResult(res)
        }

        internal suspend fun <R> append(
            head: CustomChangesDsl,
            init: suspend CustomChangesDsl.() -> R
        ): Changes<R> {
            val res = init(head)
            return head.changes.withResult(res)
        }
    }
}
