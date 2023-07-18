package com.razz.eva.uow

import com.razz.eva.domain.Principal
import com.razz.eva.events.EventPublisher
import com.razz.eva.events.UowEvent
import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import com.razz.eva.persistence.TransactionManager
import com.razz.eva.repository.EventRepository
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.TransactionalContext
import com.razz.eva.repository.TransactionalContext.Companion.transactionalContext
import com.razz.eva.uow.PersistingAccumulator.Factory.newPersistingAccumulator
import com.razz.eva.uow.PersistingMode.PARALLEL_OUT_OF_ORDER
import com.razz.eva.uow.PersistingMode.SEQUENTIAL_FIFO
import com.razz.eva.events.UowEvent.ModelEventId
import com.razz.eva.events.UowEvent.UowName
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.StringFormat
import java.time.Clock
import java.time.Instant
import java.util.UUID.randomUUID

class Persisting(
    private val transactionManager: TransactionManager<*>,
    private val modelRepos: ModelRepos,
    private val eventRepository: EventRepository,
    private val eventPublisher: EventPublisher = NoopEventPublisher,
    private val json: StringFormat = com.razz.eva.serialization.json.JsonFormat.json,
) {
    private object NoopEventPublisher : EventPublisher {
        override suspend fun publish(uowEvent: UowEvent) = Unit
    }

    internal suspend fun <PARAMS : UowParams<PARAMS>> persist(
        uowName: String,
        params: PARAMS,
        principal: Principal<*>,
        changes: Collection<Change>,
        clock: Clock,
        uowSupportsOutOfOrderPersisting: Boolean
    ) {
        val uowEvent = inTransaction(clock, uowSupportsOutOfOrderPersisting) { persisting, startedAt ->
            val events = changes.flatMap(Change::modelEvents)
            changes.forEach { change ->
                change.persist(persisting)
            }
            UowEvent(
                id = UowEvent.Id(randomUUID()),
                uowName = UowName(uowName),
                principal = principal,
                modelEvents = events.associateBy { ModelEventId.random() },
                idempotencyKey = params.idempotencyKey,
                params = json.encodeToString(params.serialization(), params),
                occurredAt = startedAt
            )
        }
        eventPublisher.publish(uowEvent)
    }

    private suspend fun inTransaction(
        clock: Clock,
        uowSupportsOutOfOrderPersisting: Boolean,
        block: (ModelPersisting, Instant) -> UowEvent
    ): UowEvent {
        val persistingMode = if (transactionManager.supportsPipelining() && uowSupportsOutOfOrderPersisting) {
            PARALLEL_OUT_OF_ORDER
        } else {
            SEQUENTIAL_FIFO
        }
        val persisting = newPersistingAccumulator(uowSupportsOutOfOrderPersisting, modelRepos)
        val uowEvent = block(persisting, clock.instant())
        transactionManager.inTransaction(
            REQUIRE_NEW,
            suspend {
                flush(persisting, uowEvent, transactionalContext(clock.instant()), persistingMode)
            }
        )
        return uowEvent
    }

    private suspend fun flush(
        accumulator: PersistingAccumulator,
        uowEvent: UowEvent,
        context: TransactionalContext,
        mode: PersistingMode
    ): Unit = when (mode) {
        PARALLEL_OUT_OF_ORDER -> coroutineScope {
            accumulator.accumulated().forEach { operation ->
                launch { operation(context) }
            }
            launch { eventRepository.add(uowEvent) }
            Unit
        }
        SEQUENTIAL_FIFO -> {
            accumulator.accumulated().forEach { operation ->
                operation(context)
            }
            eventRepository.add(uowEvent)
        }
    }
}
