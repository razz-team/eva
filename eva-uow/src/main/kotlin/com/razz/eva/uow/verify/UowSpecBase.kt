package com.razz.eva.uow.verify

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.uow.AddModel
import com.razz.eva.uow.ModelChange
import com.razz.eva.uow.Changes
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.UpdateModel
import java.util.ArrayDeque
import java.util.Deque

open class UowSpecBase<R> private constructor(
    private val result: R,
    private val executionHistory: Deque<ModelChange>,
    private val publishedEvents: Deque<ModelEvent<out ModelId<out Comparable<*>>>>,
    private val peekingPersisting: PeekingPersisting = PeekingPersisting(),
) {

    internal constructor(
        changes: Changes<R>,
    ) : this(
        result = changes.result,
        executionHistory = ArrayDeque(changes.modelChangesToPersist.filter { it !is NoopModel }),
        publishedEvents = ArrayDeque(changes.modelChangesToPersist.flatMap { it.modelEvents }),
    )

    fun verifyEnd() {
        check(executionHistory.isEmpty()) { "No more changes expected, but still present: $executionHistory" }
        check(publishedEvents.isEmpty()) { "No more events expected, but still present: $publishedEvents" }
    }

    protected fun verifyResult(verification: (R) -> Unit) {
        verification(result)
    }

    protected fun <RR : R> verifyResultAs(verification: (RR) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        verification(result as RR)
    }

    protected fun <M : Model<*, *>> verifyAdded(verify: (M) -> Unit): M {
        val model = when (
            val next = checkNotNull(executionHistory.pollFirst()) { "Expecting [AddModel] got nothing" }
        ) {
            is AddModel<*, *, *> -> {
                next.persist(peekingPersisting)
                peekingPersisting.peek()
            }
            else -> throw IllegalStateException("Expecting [AddModel] was [$next]")
        }
        @Suppress("UNCHECKED_CAST")
        verify(model as M)
        return model
    }

    protected fun <M : Model<*, *>> verifyUpdated(verify: (M) -> Unit): M {
        val model = when (
            val next = checkNotNull(executionHistory.pollFirst()) { "Expecting [UpdateModel] got nothing" }
        ) {
            is UpdateModel<*, *, *> -> {
                next.persist(peekingPersisting)
                peekingPersisting.peek()
            }
            else -> throw IllegalStateException("Expecting [UpdateModel] was [$next]")
        }
        @Suppress("UNCHECKED_CAST")
        verify(model as M)
        return model
    }

    protected fun <E : ModelEvent<out ModelId<out Comparable<*>>>> verifyEmitted(verify: (E) -> Unit): E {
        @Suppress("UNCHECKED_CAST")
        val next = checkNotNull(publishedEvents.pollFirst()) { "Expecting [ModelEvent] got nothing" } as E
        verify(next)
        return next
    }
}
