package com.razz.eva.uow

import com.razz.eva.domain.Model
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.UUID.randomUUID

class Persisting(
    private val transactionManager: TransactionManager<*>,
    private val modelRepos: ModelRepos,
    private val eventRepository: EventRepository,
    private val encoders: (UowParams<*, *>) -> Serialization.Encoder,
    private val eventPublisher: EventPublisher = NoopEventPublisher,
) {
    private object NoopEventPublisher : EventPublisher {
        override suspend fun publish(uowEvent: UowEvent) = Unit
    }

    internal suspend fun <PARAMS : UowParams<PARAMS, *>> persist(
        uowName: String,
        params: PARAMS,
        principal: Principal<*>,
        changes: Collection<Change>,
        clock: Clock,
        uowSupportsOutOfOrderPersisting: Boolean
    ): List<Model<*, *>> {
        val (uowEvent, flushed) = inTransaction(clock, uowSupportsOutOfOrderPersisting) { persisting, startedAt ->
            val events = changes.flatMap(Change::modelEvents)
            changes.forEach { change ->
                change.persist(persisting)
            }
            @Suppress("UNCHECKED_CAST")
            val ss: Serialization.Strategy<PARAMS, Serialization.Encoder> =
                params.serializationStrategy() as Serialization.Strategy<PARAMS, Serialization.Encoder>
            val e: Serialization.Encoder = params.encoder() ?: encoders(params)
            UowEvent(
                id = UowEvent.Id(randomUUID()),
                uowName = UowName(uowName),
                principal = principal,
                modelEvents = events.associateBy { ModelEventId.random() },
                idempotencyKey = params.idempotencyKey,
                params = ss(params, e),
                occurredAt = startedAt
            )
        }
        eventPublisher.publish(uowEvent)
        return flushed
    }

    private suspend fun inTransaction(
        clock: Clock,
        uowSupportsOutOfOrderPersisting: Boolean,
        block: (ModelPersisting, Instant) -> UowEvent
    ): Pair<UowEvent, List<Model<*, *>>> {
        val persistingMode = if (transactionManager.supportsPipelining() && uowSupportsOutOfOrderPersisting) {
            PARALLEL_OUT_OF_ORDER
        } else {
            SEQUENTIAL_FIFO
        }
        val now = clock.instant()
        val persisting = newPersistingAccumulator(uowSupportsOutOfOrderPersisting, modelRepos)
        val uowEvent = block(persisting, now)
        val flushed = transactionManager.inTransaction(
            REQUIRE_NEW,
            suspend { flush(persisting, uowEvent, transactionalContext(now), persistingMode) }
        )
        return uowEvent to flushed
    }

    private suspend fun flush(
        accumulator: PersistingAccumulator,
        uowEvent: UowEvent,
        context: TransactionalContext,
        mode: PersistingMode
    ): List<Model<*, *>> = when (mode) {
        PARALLEL_OUT_OF_ORDER -> coroutineScope {
            val flushed = accumulator.accumulated().map { operation ->
                async { operation(context) }
            }
            launch { eventRepository.add(uowEvent) }
            flushed.awaitAll().flatten()
        }
        SEQUENTIAL_FIFO -> {
            val flushed = accumulator.accumulated().map { operation ->
                operation(context)
            }
            eventRepository.add(uowEvent)
            flushed.flatten()
        }
    }
}
