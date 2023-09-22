package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal

class ChangesDsl internal constructor(initial: ChangesAccumulator) {
    private var tail: ChangesAccumulator? = null
    private var head: ChangesAccumulator = initial
    private fun <R> withResult(result: R): Changes<R> {
        val snapTail = tail
        return if (snapTail == null) {
            head.withResult(result)
        } else {
            snapTail.merge(head.withResult(Unit)).withResult(result)
        }
    }

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        head = head.withAdded(model)
        return model
    }

    fun <MID, E, M> update(model: M, required: Boolean = false): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        head = when (required) {
            true -> {
                head.withUpdated(model)
            }
            false -> {
                if (model.isDirty()) {
                    head.withUpdated(model)
                } else {
                    head.withUnchanged(model)
                }
            }
        }
        return model
    }

    fun <MID, E, M> notChanged(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        head = head.withUnchanged(model)
        return model
    }

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        uow: UOW,
        principal: PRINCIPAL,
        params: () -> PARAMS,
    ): RESULT
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        val subChanges = uow.tryPerform(principal, params())
        val snapTail = tail
        tail = if (snapTail == null) {
            head.merge(subChanges)
        } else {
            snapTail.merge(head.merge(subChanges).withResult(Unit))
        }
        head = ChangesAccumulator()
        return subChanges.result
    }

    companion object {
        internal suspend inline fun <R> changes(
            changes: ChangesAccumulator,
            @Suppress("REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE")
            init: suspend ChangesDsl.() -> R
        ): Changes<R> {
            val dsl = ChangesDsl(changes)
            val res = init(dsl)
            return dsl.withResult(res)
        }
    }
}
