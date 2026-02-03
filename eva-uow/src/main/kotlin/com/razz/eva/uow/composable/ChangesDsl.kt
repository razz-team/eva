package com.razz.eva.uow.composable

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.uow.OtelAttributes.MODEL_ID
import com.razz.eva.tracing.getEvaTracer
import com.razz.eva.tracing.use
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.UowParams
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import kotlin.reflect.KClass

class ChangesDsl internal constructor(initial: ChangesAccumulator, private val otel: OpenTelemetry) {
    private var tail: ChangesAccumulator? = null
    private var head: ChangesAccumulator = initial

    private fun <R> withResult(result: R): Changes<R> {
        return tail?.merge(head)?.withResult(result) ?: head.withResult(result)
    }

    // Under no circumstances should this method accept a model that is not new
    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        require(model.isNew()) {
            "Attempted to register ${if (model.isDirty()) "changed" else "unchanged"} " +
                "model [${model.id().stringValue()}] as new"
        }
        head = head.withAddedModel(model)
        return model
    }

    // Models can come from two sources in composable UoWs:
    // 1. Via ModelParam - wrapped in SnapshotState, isDirty() returns true after modification
    // 2. Via closure capture from outer scope - still in NewState, need isNew() check for backward compatibility
    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        require(model.isDirty() || model.isNew()) {
            "Attempted to register unchanged model [${model.id().stringValue()}] as changed"
        }
        head = head.withUpdatedModel(model)
        return model
    }

    // Models can come from two sources in composable UoWs:
    // 1. Via ModelParam - wrapped in SnapshotState, isPersisted() returns true initially
    // 2. Via closure capture from outer scope - still in NewState, need isNew() check for backward compatibility
    fun <MID, E, M> notChanged(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        require(model.isPersisted() || model.isNew()) {
            "Attempted to register changed model [${model.id().stringValue()}] as unchanged"
        }
        head = head.withUnchangedModel(model)
        return model
    }

    fun <E : CreatableEntity> add(entity: E): E {
        head = head.withAddedEntity(entity)
        return entity
    }

    fun <E : DeletableEntity> delete(entity: E): E {
        head = head.withDeletedEntity(entity)
        return entity
    }

    inline fun <reified E : DeletableEntity, K : EntityKey<E>> delete(key: K): K {
        deleteByKeyInternal(key, E::class)
        return key
    }

    @PublishedApi
    internal fun <E : DeletableEntity, K : EntityKey<E>> deleteByKeyInternal(key: K, entityClass: KClass<E>) {
        head = head.withDeletedEntityByKey(key, entityClass)
    }

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        uow: UOW,
        principal: PRINCIPAL,
        params: InstantiationContext.Internal.() -> PARAMS,
    ): RESULT
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        val span = uowSpan(uow.name())
        return span.use {
            val subChanges = performingSpan(uow.name()).use {
                uow.tryPerform(principal, params(InstantiationContext.Internal(0)))
            }
            span.setAttribute(
                MODEL_ID,
                subChanges.modelChangesToPersist.map { it.id.stringValue() },
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
            init: suspend ChangesDsl.() -> R,
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
