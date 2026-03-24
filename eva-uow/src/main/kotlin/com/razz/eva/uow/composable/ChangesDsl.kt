package com.razz.eva.uow.composable

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.domain.UpdatableEntity
import com.razz.eva.uow.OtelAttributes.MODEL_ID
import com.razz.eva.tracing.getEvaTracer
import com.razz.eva.tracing.use
import com.razz.eva.uow.AddModel
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.UpdateModel
import com.razz.eva.uow.UowParams
import io.opentelemetry.api.trace.Span
import kotlin.reflect.KClass

class ChangesDsl internal constructor(
    private val executionContext: ExecutionContext,
) {
    private var changes: ChangesAccumulator = executionContext.inheritedChanges ?: ChangesAccumulator()
    private val inheritedModelIds: MutableSet<ModelId<out Comparable<*>>> = changes.modelIds().toMutableSet()

    private fun <R> withResult(result: R): Changes<R> = changes.withResult(result)

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        require(model.isNew()) {
            "Attempted to register ${if (model.isDirty()) "changed" else "unchanged"} " +
                "model [${model.id().stringValue()}] as new"
        }
        changes = changes.withAddedModel(model)
        return model
    }

    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        val existing = changes.changeFor(model.id())
        if (existing != null && model.id() in inheritedModelIds) {
            val newEvents = model.modelEvents()
            check(newEvents isSuccessorOf existing.modelEvents) {
                "Failed to merge changes for model [${model.id().stringValue()}]"
            }
            val merged = when (existing) {
                is AddModel<*, *, *> -> AddModel(model, newEvents)
                is UpdateModel<*, *, *> -> UpdateModel(model, newEvents)
                is NoopModel -> UpdateModel(model, newEvents)
            }
            changes = changes.withReplacedModelChange(model.id(), merged)
        } else {
            require(model.isDirty()) {
                "Attempted to register ${if (model.isNew()) "new" else "unchanged"} " +
                    "model [${model.id().stringValue()}] as changed"
            }
            changes = changes.withUpdatedModel(model)
        }
        return model
    }

    fun <MID, E, M> notChanged(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        val existing = changes.changeFor(model.id())
        if (existing == null || model.id() !in inheritedModelIds) {
            require(model.isPersisted()) {
                "Attempted to register ${if (model.isNew()) "new" else "changed"} " +
                    "model [${model.id().stringValue()}] as unchanged"
            }
            changes = changes.withUnchangedModel(model)
        }
        return model
    }

    fun <E : CreatableEntity> add(entity: E): E {
        changes = changes.withAddedEntity(entity)
        return entity
    }

    fun <E : UpdatableEntity> update(entity: E): E {
        changes = changes.withUpdatedEntity(entity)
        return entity
    }

    fun <E : DeletableEntity> delete(entity: E): E {
        changes = changes.withDeletedEntity(entity)
        return entity
    }

    inline fun <reified E : UpdatableEntity, K : EntityKey<E>> update(key: K): K {
        updateByKeyInternal(key, E::class)
        return key
    }

    @PublishedApi
    internal fun <E : UpdatableEntity, K : EntityKey<E>> updateByKeyInternal(key: K, entityClass: KClass<E>) {
        changes = changes.withUpdatedEntityByKey(key, entityClass)
    }

    inline fun <reified E : DeletableEntity, K : EntityKey<E>> delete(key: K): K {
        deleteByKeyInternal(key, E::class)
        return key
    }

    @PublishedApi
    internal fun <E : DeletableEntity, K : EntityKey<E>> deleteByKeyInternal(key: K, entityClass: KClass<E>) {
        changes = changes.withDeletedEntityByKey(key, entityClass)
    }

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        uowFactory: (ExecutionContext) -> UOW,
        principal: PRINCIPAL,
        params: InstantiationContext.Internal.() -> PARAMS,
    ): RESULT
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : UnitOfWork<PRINCIPAL, PARAMS, RESULT> {
        val uow = uowFactory(executionContext.withInheritedChanges(changes))
        val span = uowSpan(uow.name())
        return span.use {
            val subChanges = performingSpan(uow.name()).use {
                uow.tryPerform(principal, params(InstantiationContext.Internal(0)))
            }
            span.setAttribute(
                MODEL_ID,
                subChanges.modelChangesToPersist.map { it.id.stringValue() },
            )
            if (subChanges.modelChangesToPersist.isNotEmpty() || subChanges.entityChangesToPersist.isNotEmpty()) {
                changes = ChangesAccumulator.from(subChanges)
                inheritedModelIds.addAll(changes.modelIds())
            }
            subChanges.result
        }
    }

    companion object {
        internal suspend inline fun <R> changes(
            executionContext: ExecutionContext,
            @Suppress("REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE")
            init: suspend ChangesDsl.() -> R,
        ): Changes<R> {
            val dsl = ChangesDsl(executionContext)
            val res = init(dsl)
            return dsl.withResult(res)
        }
    }

    private fun uowSpan(name: String): Span = executionContext.otel.getEvaTracer()
        .spanBuilder(name)
        .startSpan()

    private fun performingSpan(name: String): Span = executionContext.otel.getEvaTracer()
        .spanBuilder("$name-perform")
        .startSpan()

    private infix fun List<ModelEvent<*>>
        .isSuccessorOf(events: List<ModelEvent<*>>): Boolean {
        if (size <= events.size) {
            return false
        }
        events.forEachIndexed { i, e ->
            if (this[i] !== e) {
                return false
            }
        }
        return true
    }
}
