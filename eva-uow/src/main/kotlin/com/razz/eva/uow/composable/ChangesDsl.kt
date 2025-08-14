package com.razz.eva.uow.composable

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.tracing.getEvaTracer
import com.razz.eva.tracing.use
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.OtelAttributes.MODEL_ID
import com.razz.eva.uow.UowParams
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span

class ChangesDsl internal constructor(initial: ChangesAccumulator, private val otel: OpenTelemetry) {
    private var tail: ChangesAccumulator? = null
    private var head: ChangesAccumulator = initial

    private fun <R> withResult(result: R): Changes<R> {
        val changes = tail?.merge(head) ?: head
        return changes.withResult(result, expectChanges = false)
    }

    // Under no circumstances should this method accept a model that is not new
    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        require(model.isNew()) {
            "Attempted to register ${if (model.isDirty()) "changed" else "unchanged"} " +
                "model [${model.id().stringValue()}] as new"
        }
        head = head.withAdded(model)
        return model
    }

    // It is possible in a sound program to register a model as changed
    // that was created in different sub-uow and passed as a parameter, ie:
    // val uow0 = UnitOfWork0<..., Model>(...) {
    //     override fun tryPerform(...): Changes<Model> = changes { add(newModel()) }
    // }
    // val uow1 = UnitOfWork1(...) {
    //     data classParams(val model: Model)
    //     override fun tryPerform(... params: Params) = changes { ... update(params.model.modify()) }
    //     ^ there is no control whether `model` was queried from db and somehow modified prior to being passed to uow
    //       or it was created out of scope of uow and never persisted hence `::isNew` is true
    // }
    // val bigUow = UnitOfWorkBig(...) {
    //     override fun tryPerform(...) = changes {
    //         ...
    //         val model = uow0.execute(...)
    //         ...
    //         val ... = uow1.execute(...) { UnitOfWork1.Params(model) }
    //         ...
    //     }
    //
    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        require(model.isDirty() || model.isNew()) {
            "Attempted to register unchanged model [${model.id().stringValue()}] as changed"
        }
        head = head.withUpdated(model)
        return model
    }

    // It is possible in a sound program to register a model as unchanged
    // that was created in different sub-uow and passed as a parameter, ie:
    // val uow0 = UnitOfWork0<..., Model>(...) {
    //     override fun tryPerform(...): Changes<Model> = changes { add(newModel()) }
    // }
    // val uow1 = UnitOfWork1(...) {
    //     data classParams(val model: Model)
    //     override fun tryPerform(... params: Params) = changes { ... if (dontChange) { notChanged(params.model) } }
    //     ^ there is no control whether `model` was queried from db and prior to being passed to uow
    //       or it was created out of scope of uow and never persisted hence `::isNew` is true
    // }
    // val bigUow = UnitOfWorkBig(...) {
    //     override fun tryPerform(...) = changes {
    //         ...
    //         val model = uow0.execute(...)
    //         ...
    //         val ... = uow1.execute(...) { UnitOfWork1.Params(model) }
    //         ...
    //     }
    //
    fun <MID, E, M> notChanged(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        require(model.isPersisted() || model.isNew()) {
            "Attempted to register changed model [${model.id().stringValue()}] as unchanged"
        }
        head = head.withUnchanged(model)
        return model
    }

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        uow: UOW,
        principal: PRINCIPAL,
        params: InstantiationContext.() -> PARAMS,
    ): RESULT
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        val span = uowSpan(uow.name())
        return span.use {
            val subChanges = performingSpan(uow.name()).use {
                uow.tryPerform(principal, params(InstantiationContext(0)))
            }
            span.setAttribute(
                MODEL_ID,
                subChanges.toPersist.map { it.id.stringValue() }
            )
            mergingSpan(uow.name()).use {
                tail = tail?.merge(head.merge(subChanges)) ?: head.merge(subChanges)
            }
            head = ChangesAccumulator()
            subChanges.result
        }
    }

    companion object {
        internal suspend inline fun <R> changes(
            changes: ChangesAccumulator,
            otel: OpenTelemetry,
            @Suppress("REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE")
            init: suspend ChangesDsl.() -> R
        ): Changes<R> {
            val dsl = ChangesDsl(changes, otel)
            val res = init(dsl)
            return dsl.withResult(res)
        }
    }

    private fun uowSpan(name: String): Span = otel.getEvaTracer()
        .spanBuilder(name)
        .startSpan()

    private fun performingSpan(name: String): Span = otel.getEvaTracer()
        .spanBuilder("$name-perform")
        .startSpan()

    private fun mergingSpan(name: String): Span = otel.getEvaTracer()
        .spanBuilder("$name-merge")
        .startSpan()
}
