package com.razz.eva.uow.verify

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.uow.AddEntity
import com.razz.eva.uow.AddModel
import com.razz.eva.uow.Changes
import com.razz.eva.uow.DeleteEntity
import com.razz.eva.uow.EntityChange
import com.razz.eva.uow.ModelChange
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.UpdateModel
import java.util.ArrayDeque
import java.util.Deque

open class UowSpecBase<R> private constructor(
    private val result: R,
    private val modelChangeHistory: Deque<ModelChange>,
    private val entityChangeHistory: Deque<EntityChange>,
    private val publishedEvents: Deque<ModelEvent<out ModelId<out Comparable<*>>>>,
    private val peekingModelPersisting: PeekingModelPersisting = PeekingModelPersisting(),
    private val peekingEntityPersisting: PeekingEntityPersisting = PeekingEntityPersisting(),
) {

    internal constructor(
        changes: Changes<R>,
    ) : this(
        result = changes.result,
        modelChangeHistory = ArrayDeque(changes.modelChangesToPersist.filter { it !is NoopModel }),
        entityChangeHistory = ArrayDeque(changes.entityChangesToPersist),
        publishedEvents = ArrayDeque(changes.modelChangesToPersist.flatMap { it.modelEvents }),
    )

    fun verifyEnd() {
        check(modelChangeHistory.isEmpty()) {
            "No more model changes expected, but still present: $modelChangeHistory"
        }
        check(entityChangeHistory.isEmpty()) {
            "No more entity changes expected, but still present: $entityChangeHistory"
        }
        check(publishedEvents.isEmpty()) {
            "No more events expected, but still present: $publishedEvents"
        }
    }

    @PublishedApi
    internal fun verifyResultInternal(verification: (R) -> Unit) {
        verification(result)
    }

    @PublishedApi
    internal fun <RR : R> verifyResultAsInternal(verification: (RR) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        verification(result as RR)
    }

    @PublishedApi
    internal fun <M : Model<*, *>> verifyAddedModel(verify: (M) -> Unit): M {
        val model = when (
            val next = checkNotNull(modelChangeHistory.pollFirst()) { "Expecting [AddModel] got nothing" }
        ) {
            is AddModel<*, *, *> -> {
                next.persist(peekingModelPersisting)
                peekingModelPersisting.peek()
            }
            else -> throw IllegalStateException("Expecting [AddModel] was [$next]")
        }
        @Suppress("UNCHECKED_CAST")
        verify(model as M)
        return model
    }

    @PublishedApi
    internal fun <M : Model<*, *>> verifyUpdatedModel(verify: (M) -> Unit): M {
        val model = when (
            val next = checkNotNull(modelChangeHistory.pollFirst()) { "Expecting [UpdateModel] got nothing" }
        ) {
            is UpdateModel<*, *, *> -> {
                next.persist(peekingModelPersisting)
                peekingModelPersisting.peek()
            }
            else -> throw IllegalStateException("Expecting [UpdateModel] was [$next]")
        }
        @Suppress("UNCHECKED_CAST")
        verify(model as M)
        return model
    }

    @PublishedApi
    internal fun <E : ModelEvent<out ModelId<out Comparable<*>>>> verifyEmittedEvent(verify: (E) -> Unit): E {
        @Suppress("UNCHECKED_CAST")
        val next = checkNotNull(publishedEvents.pollFirst()) { "Expecting [ModelEvent] got nothing" } as E
        verify(next)
        return next
    }

    @PublishedApi
    internal fun <E : CreatableEntity> verifyAddedEntity(verify: (E) -> Unit): E {
        val entity = when (
            val next = checkNotNull(entityChangeHistory.pollFirst()) { "Expecting [AddEntity] got nothing" }
        ) {
            is AddEntity<*> -> {
                next.persist(peekingEntityPersisting)
                peekingEntityPersisting.peek()
            }
            else -> throw IllegalStateException("Expecting [AddEntity] was [$next]")
        }
        @Suppress("UNCHECKED_CAST")
        verify(entity as E)
        return entity
    }

    @PublishedApi
    internal fun <E : DeletableEntity> verifyDeletedEntity(verify: (E) -> Unit): E {
        val entity = when (
            val next = checkNotNull(entityChangeHistory.pollFirst()) { "Expecting [DeleteEntity] got nothing" }
        ) {
            is DeleteEntity<*> -> {
                next.persist(peekingEntityPersisting)
                peekingEntityPersisting.peek()
            }
            else -> throw IllegalStateException("Expecting [DeleteEntity] was [$next]")
        }
        @Suppress("UNCHECKED_CAST")
        verify(entity as E)
        return entity
    }
}
