package com.razz.eva.uow.verify

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.UpdatableEntity
import com.razz.eva.uow.AddEntity
import com.razz.eva.uow.AddModel
import com.razz.eva.uow.Changes
import com.razz.eva.uow.DeleteEntity
import com.razz.eva.uow.DeleteEntityByKey
import com.razz.eva.uow.EntityChange
import com.razz.eva.uow.ModelChange
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.UpdateEntity
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
            "No more events expected, but still present: ${publishedEvents.joinToString { describeEvent(it) }}. " +
                "Every event a change raises has to be asserted by its own emits call, so a change raising " +
                "two events needs two of them"
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

    /**
     * Asserts that the unit of work result denotes the model registered as this change: same id, and the
     * change carries every event the result carries.
     *
     * Not identity. [com.razz.eva.uow.UnitOfWorkExecutor] resolves a model result against the flushed set
     * by id (`returnRoundtrippedModels` defaults to true), so the id is what the framework guarantees the
     * caller receives. A composed parent may legitimately return the instance a child registered after
     * merging a newer one over it, and `roundtrip { p -> p(model) }` seeds its value from the change set as
     * of that moment, which a later merge supersedes -- both return an earlier instance of the same model,
     * and both are correct at runtime.
     *
     * The event check is what an id alone cannot do: it rejects a result carrying state that was never
     * registered, so it will not be persisted. It is the same rule the composable
     * [com.razz.eva.uow.composable.ChangesDsl] merge applies (`isSameAs` / `isSuccessorOf`): the change's
     * events have to hold the result's events by identity, in order, from the same start. Identity, because
     * raising an event appends to the existing instances rather than rebuilding them, so a genuine earlier
     * instance of the same model shares its event objects with the change.
     */
    @PublishedApi
    internal fun verifyResultIsAddedModel(change: Model<*, *>) = verifyResultIsModel(change, "added")

    @PublishedApi
    internal fun verifyResultIsUpdatedModel(change: Model<*, *>) = verifyResultIsModel(change, "updated")

    private fun verifyResultIsModel(change: Model<*, *>, changeKind: String) {
        val actual: Any? = result
        if (actual !is Model<*, *>) {
            error(
                "Result is not a model at all, so it cannot be the $changeKind model " +
                    "${describe(change)}. Result was ${render(actual)}",
            )
        }
        if (actual.id() != change.id()) {
            error(
                "Result is not the $changeKind model: the ids differ. The change at this position is " +
                    "${describe(change)}, the result is ${describe(actual)}. Check that the verify calls " +
                    "are in the same order as the registered changes.",
            )
        }
        val changeEvents = eventsOf(change)
        val resultEvents = eventsOf(actual)
        check(changeEvents carriesAtLeast resultEvents) {
            val reason = if (resultEvents.size > changeEvents.size) {
                "the result carries events the change does not, so they would never be persisted"
            } else {
                "the result carries a different history, so it is not an instance of this change"
            }
            "Result is not the $changeKind model [${change.id().stringValue()}]: $reason. The change holds " +
                "${eventNames(changeEvents)}, the result holds ${eventNames(resultEvents)}"
        }
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
    internal fun <E : UpdatableEntity> verifyUpdatedEntity(verify: (E) -> Unit): E {
        val entity = when (
            val next = checkNotNull(entityChangeHistory.pollFirst()) { "Expecting [UpdateEntity] got nothing" }
        ) {
            is UpdateEntity<*> -> {
                next.persist(peekingEntityPersisting)
                peekingEntityPersisting.peek()
            }
            else -> throw IllegalStateException("Expecting [UpdateEntity] was [$next]")
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

    @PublishedApi
    internal fun <E : DeletableEntity, K : EntityKey<E>> verifyDeletedEntityByKey(verify: (K) -> Unit): K {
        val key = when (
            val next = checkNotNull(entityChangeHistory.pollFirst()) { "Expecting [EntityKey] got nothing" }
        ) {
            is DeleteEntityByKey<*, *> -> {
                next.persist(peekingEntityPersisting)
                peekingEntityPersisting.peekKey()
            }
            else -> throw IllegalStateException("Expecting [EntityKey] was [$next]")
        }
        @Suppress("UNCHECKED_CAST")
        verify(key as K)
        return key
    }

    private fun describe(model: Model<*, *>): String {
        val state = when {
            model.isNew() -> "new"
            model.isDirty() -> "dirty"
            model.isPersisted() -> "persisted"
            else -> "unknown state"
        }
        return "${model::class.simpleName}[id = ${model.id().stringValue()}, $state, " +
            "version = ${model.version().version}, events = ${eventNames(eventsOf(model))}]"
    }

    private fun render(value: Any?): String = when (value) {
        null -> "[null]"
        else -> "[${value::class.simpleName}: $value]"
    }

    private fun eventNames(events: List<ModelEvent<*>>): String =
        events.joinToString(prefix = "[", postfix = "]") { it.eventName() }

    private fun describeEvent(event: ModelEvent<*>): String =
        "${event.eventName()}(${event.modelName} ${event.modelId.stringValue()})"

    @Suppress("UNCHECKED_CAST")
    private fun eventsOf(model: Model<*, *>): List<ModelEvent<*>> =
        (model as Model<ModelId<out Comparable<*>>, ModelEvent<ModelId<out Comparable<*>>>>).modelEvents()

    private infix fun List<ModelEvent<*>>.carriesAtLeast(events: List<ModelEvent<*>>): Boolean {
        if (size < events.size) {
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
