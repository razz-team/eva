package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.Principal
import com.razz.eva.events.UowEvent
import com.razz.eva.persistence.ConnectionAcquisitionCounter
import com.razz.eva.persistence.ConnectionMode
import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.persistence.PrimaryConnectionRequiredFlag
import com.razz.eva.tracing.getEvaMeter
import com.razz.eva.tracing.getEvaTracer
import com.razz.eva.tracing.use
import com.razz.eva.uow.OtelAttributes.ATTEMPT
import com.razz.eva.uow.OtelAttributes.EVENT_NAME
import com.razz.eva.uow.OtelAttributes.EXCEPTION
import com.razz.eva.uow.OtelAttributes.MODEL_ID
import com.razz.eva.uow.OtelAttributes.MODEL_NAME
import com.razz.eva.uow.OtelAttributes.PRINCIPAL_ID
import com.razz.eva.uow.OtelAttributes.SPAN_PERFORM
import com.razz.eva.uow.OtelAttributes.SPAN_PERSIST
import com.razz.eva.uow.OtelAttributes.TABLE
import com.razz.eva.uow.OtelAttributes.UOW_ID
import com.razz.eva.uow.OtelAttributes.UOW_NAME
import com.razz.eva.uow.OtelAttributes.UOW_OPERATION
import com.razz.eva.uow.OtelAttributes.WILL_RETRY
import com.razz.eva.uow.UnitOfWorkExecutor.ClassToUow
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.time.Instant
import java.time.InstantSource
import kotlin.collections.flatMap
import kotlin.reflect.KClass

infix fun <PRINCIPAL, PARAMS, RESULT, UOW> KClass<UOW>.withFactory(
    factory: (ExecutionContext) -> UOW,
) where PRINCIPAL : Principal<*>,
      PARAMS : UowParams<PARAMS>,
      UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *>,
      RESULT : Any =
    ClassToUow(this, factory)

class UnitOfWorkExecutor(
    factories: List<ClassToUow<*, *, *, *>>,
    private val persisting: Persisting,
    private val clock: InstantSource,
    private val openTelemetry: OpenTelemetry,
) {
    @ExecutionContextApi
    fun executionContext() = ExecutionContext(clock, openTelemetry)

    class ClassToUow<PRINCIPAL, PARAMS, RESULT, UOW> internal constructor(
        internal val uowClass: KClass<UOW>,
        internal val uowFactory: (ExecutionContext) -> UOW,
    ) where PRINCIPAL : Principal<*>,
          UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *>,
          PARAMS : UowParams<PARAMS>,
          RESULT : Any

    private val logger = KotlinLogging.logger {}
    private val classToFactory = factories.groupBy(ClassToUow<*, *, *, *>::uowClass).mapValues {
        it.value.singleOrNull()?.uowFactory
            ?: throw IllegalArgumentException("Attempted to register multiple factories for ${it.key.simpleName}")
    }

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        principal: PRINCIPAL,
        uowFactory: (ExecutionContext) -> UOW,
        params: InstantiationContext.() -> PARAMS,
    ): RESULT where PRINCIPAL : Principal<*>,
          PARAMS : UowParams<PARAMS>,
          RESULT : Any,
          UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        return execute(
            principal = principal,
            uowName = "<dynamic uow factory>",
            uowFactory = uowFactory,
            params = params,
        )
    }

    private suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        principal: PRINCIPAL,
        uowName: String,
        uowFactory: (ExecutionContext) -> UOW,
        params: InstantiationContext.() -> PARAMS,
    ): RESULT where PRINCIPAL : Principal<*>,
          PARAMS : UowParams<PARAMS>,
          RESULT : Any,
          UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        val startTime = System.nanoTime()
        val uowSpan = uowSpan().apply {
            updateName(uowName)
            setAttribute(UOW_NAME, uowName)
        }
        var name = uowName
        try {
            var currentAttempt = 0
            while (true) {
                val now = clock.instant()
                val uow = uowFactory(ExecutionContext(Clocks.fixedUTC(now), openTelemetry))
                if (currentAttempt == 0) {
                    name = uow.name()
                    uowSpan.updateName(name)
                    uowSpan.setAttribute(UOW_NAME, name)
                }
                val constructedParams = params(InstantiationContext.External(currentAttempt))
                val attempted = attempt(uow, principal, constructedParams, now, name, uowSpan)
                val committed = when (attempted) {
                    is Attempted.Conflict -> {
                        val ex = attempted.ex
                        uowSpan.addEvent(
                            "persistence.exception",
                            Attributes.of(
                                SpanAttributes.peristenceException,
                                ex::class.simpleName ?: "Unknown",
                                SpanAttributes.modelIds,
                                (ex as? PersistenceException.ModelAware)?.modelIds?.map { it.stringValue() }
                                    ?: listOf(),
                            ),
                        )
                        val config = uow.configuration()
                        val willRetry = config.retry.shouldRetry(currentAttempt, ex)
                        incrementPersistenceExceptionMetric(ex, name, currentAttempt, willRetry)
                        if (willRetry) {
                            currentAttempt += 1
                            logger.warn(ex) {
                                "Retrying UnitOfWork: ${uow.name()}. Attempt: $currentAttempt"
                            }
                            continue
                        }
                        return uow.onFailure(constructedParams, ex)
                    }
                    is Attempted.Committed -> attempted
                }
                persisting.publish(committed.uowEvent)
                uowSpan.setAttribute(
                    UOW_ID,
                    committed.uowEvent.id.toString(),
                )
                return if (uow.configuration().returnRoundtrippedModels) {
                    result(committed.changes, committed.persisted)
                } else {
                    committed.changes.result
                }
            }
        } catch (ex: Exception) {
            uowSpan.recordException(ex)
            throw ex
        } finally {
            val endTime = System.nanoTime()
            val elapsedTime = endTime - startTime
            timer.record(elapsedTime, Attributes.of(AttributeKey.stringKey("uow.name"), name))
            uowSpan.end()
        }
    }

    private sealed interface Attempted<out RESULT : Any> {

        class Committed<RESULT : Any>(
            val changes: Changes<RESULT>,
            val uowEvent: UowEvent,
            val persisted: List<Model<*, *>>,
        ) : Attempted<RESULT>

        class Conflict(val ex: PersistenceException) : Attempted<Nothing>
    }

    private suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> attempt(
        uow: UOW,
        principal: PRINCIPAL,
        params: PARAMS,
        now: Instant,
        name: String,
        uowSpan: Span,
    ): Attempted<RESULT> where PRINCIPAL : Principal<*>,
          PARAMS : UowParams<PARAMS>,
          RESULT : Any,
          UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        return when (uow.configuration().writeTxScope) {
            WriteTxScope.FLUSH -> {
                // tryPerform stays outside the conflict handling: its persistence exceptions
                // propagate raw instead of routing to retry/onFailure
                val changes = performAttempt(
                    uow = uow,
                    principal = principal,
                    params = params,
                    name = name,
                    uowSpan = uowSpan,
                )
                try {
                    persistAttempt(
                        uow = uow,
                        principal = principal,
                        params = params,
                        changes = changes,
                        now = now,
                        name = name,
                        uowSpan = uowSpan,
                        connectionMode = REQUIRE_NEW,
                    )
                } catch (ex: PersistenceException) {
                    Attempted.Conflict(ex)
                }
            }
            WriteTxScope.FULL_UOW -> try {
                persisting.transactionally {
                    val changes = performAttempt(
                        uow = uow,
                        principal = principal,
                        params = params,
                        name = name,
                        uowSpan = uowSpan,
                    )
                    persistAttempt(
                        uow = uow,
                        principal = principal,
                        params = params,
                        changes = changes,
                        now = now,
                        name = name,
                        uowSpan = uowSpan,
                        connectionMode = REQUIRE_EXISTING,
                    )
                }
            } catch (ex: PersistenceException) {
                Attempted.Conflict(ex)
            }
        }
    }

    private suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> performAttempt(
        uow: UOW,
        principal: PRINCIPAL,
        params: PARAMS,
        name: String,
        uowSpan: Span,
    ): Changes<RESULT> where PRINCIPAL : Principal<*>,
          PARAMS : UowParams<PARAMS>,
          RESULT : Any,
          UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        val changes = instrumentedPerform(name) { acquisitions ->
            withContext(PrimaryConnectionRequiredFlag + acquisitions + uowSpan.asContextElement()) {
                performingSpan(name).use {
                    uow.tryPerform(principal, params)
                }
            }
        }
        check(!changes.stubbed) {
            "$name returned stubChanges; it builds test doubles only, a real UnitOfWork must go through changes { }"
        }
        uowSpan.setAttribute(
            MODEL_ID,
            changes.modelChangesToPersist.map { it.id.stringValue() },
        )
        uowSpan.setAttribute(
            PRINCIPAL_ID,
            principal.id.toString(),
        )
        incrementEventsMetric(changes.modelChangesToPersist, name)
        return changes
    }

    private suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> persistAttempt(
        uow: UOW,
        principal: PRINCIPAL,
        params: PARAMS,
        changes: Changes<RESULT>,
        now: Instant,
        name: String,
        uowSpan: Span,
        connectionMode: ConnectionMode,
    ): Attempted.Committed<RESULT> where PRINCIPAL : Principal<*>,
          PARAMS : UowParams<PARAMS>,
          RESULT : Any,
          UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        val (uowEvent, persisted) = timed(persistTimer, name) {
            withContext(uowSpan.asContextElement()) {
                persistingSpan(name).use {
                    persisting.persist(
                        uowName = uow.name(),
                        params = params,
                        principal = principal,
                        modelChanges = changes.modelChangesToPersist,
                        entityChanges = changes.entityChangesToPersist,
                        now = now,
                        uowSupportsOutOfOrderPersisting = uow.configuration().supportsOutOfOrderPersisting,
                        connectionMode = connectionMode,
                    )
                }
            }
        }
        return Attempted.Committed(changes, uowEvent, persisted)
    }

    private fun <RESULT> result(
        changes: Changes<RESULT>,
        persisted: List<Model<*, *>>,
    ): RESULT {
        // roundtrip { } builder rebuilds the result from persisted models; otherwise default-roundtrip the result.
        val builder = changes.resultBuilder
        if (builder != null) {
            val byId = persisted.associateBy { it.id() }
            @Suppress("UNCHECKED_CAST")
            return builder(ChangeSetLookup { byId[it] }) as RESULT
        }
        return roundtrippedResult(changes, persisted)
    }

    private fun <RESULT> roundtrippedResult(
        changes: Changes<RESULT>,
        persisted: List<Model<*, *>>,
    ) = when (val result = changes.result) {
        is Model<*, *> -> {
            // don't try to find persisted data for returned values such as `notChanged(model)`
            if (changes.modelChangesToPersist.any { it !is NoopModel && it.id == result.id() }) {
                @Suppress("UNCHECKED_CAST")
                val roundtripped = persisted.singleOrNull { it.id() == result.id() } as? RESULT
                if (roundtripped == null) logger.warn {
                    "Unable to find returned model [${result.id().stringValue()}] in persisted changes"
                }
                roundtripped ?: result
            } else result
        }
        is Collection<*> -> {
            val models = result.filterIsInstance<Model<*, *>>()
            if (models.isEmpty()) result
            else {
                val toPersist = changes.modelChangesToPersist
                    .mapNotNull { if (it is NoopModel) null else it.id }.toSet()
                // don't try to find persisted data for returned values such as `notChanged(model)`
                val persistedById = persisted.associateBy { it.id() }
                val matched = models.mapNotNull { model ->
                    if (toPersist.contains(model.id())) {
                        persistedById[model.id()]
                    } else model
                }
                @Suppress("UNCHECKED_CAST")
                if (matched.size == models.size) matched as RESULT
                else {
                    val notFound = models.filter { !matched.contains(it) }.joinToString { it.id().stringValue() }
                    logger.warn { "Unable to find returned models in persisted changes: $notFound" }
                    result
                }
            }
        }
        else -> result
    }

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        target: KClass<UOW>,
        principal: PRINCIPAL,
        params: InstantiationContext.() -> PARAMS,
    ): RESULT where PRINCIPAL : Principal<*>,
          PARAMS : UowParams<PARAMS>,
          RESULT : Any,
          UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        return execute(
            principal = principal,
            uowName = target.java.simpleName,
            uowFactory = { exCtx -> create(exCtx, target) },
            params = params,
        )
    }

    private suspend fun Retry?.shouldRetry(currentAttempt: Int, ex: PersistenceException): Boolean =
        this?.getNextDelay(currentAttempt, ex)?.let {
            delay(it.toMillis())
            true
        } ?: false

    @Suppress("UNCHECKED_CAST", "UnreachableCode")
    private fun <PRINCIPAL, PARAMS, RESULT, UOW> create(
        executionContext: ExecutionContext,
        target: KClass<UOW>,
    ): UOW where PRINCIPAL : Principal<*>,
          PARAMS : UowParams<PARAMS>,
          RESULT : Any,
          UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        val factory = classToFactory[target] ?: throw UowFactoryNotFoundException(target)
        return (factory as (ExecutionContext) -> UOW)(executionContext)
    }

    private fun incrementEventsMetric(modelChanges: List<ModelChange>, uowName: String) {
        modelChanges.flatMap { it.modelEvents }
            .forEach { modelEvent ->
                eventsMetric.add(
                    1,
                    Attributes.of(
                        AttributeKey.stringKey(MODEL_NAME), modelEvent.modelName,
                        AttributeKey.stringKey(EVENT_NAME), modelEvent.eventName(),
                        AttributeKey.stringKey(UOW_NAME), uowName,
                    ),
                )
            }
    }

    private val eventsMetric = openTelemetry.getEvaMeter()
        .counterBuilder("model.event")
        .setDescription("Number of model events emitted")
        .setUnit("count")
        .build()

    private fun incrementPersistenceExceptionMetric(
        ex: PersistenceException,
        uowName: String,
        attempt: Int,
        willRetry: Boolean,
    ) {
        runCatching {
            persistenceExceptionMetric.add(
                1,
                Attributes.of(
                    AttributeKey.stringKey(UOW_NAME), uowName,
                    AttributeKey.stringKey(EXCEPTION), ex::class.simpleName ?: "Unknown",
                    AttributeKey.stringKey(TABLE), tableName(ex),
                    AttributeKey.longKey(ATTEMPT), attempt.toLong(),
                    AttributeKey.booleanKey(WILL_RETRY), willRetry,
                ),
            )
        }
    }

    private fun tableName(ex: PersistenceException): String =
        (ex as? PersistenceException.TableAware)?.tableName ?: "unknown"

    private val persistenceExceptionMetric = openTelemetry.getEvaMeter()
        .counterBuilder("uow.persistence_exception")
        .setDescription("Number of persistence exceptions caught during UnitOfWork execution")
        .setUnit("count")
        .build()

    private fun uowSpan() = openTelemetry.getEvaTracer()
        .spanBuilder("Uow")
        .startSpan()

    private fun performingSpan(name: String) = openTelemetry.getEvaTracer()
        .spanBuilder("$name-$SPAN_PERFORM")
        .setAttribute(UOW_OPERATION, SPAN_PERFORM)
        .setAttribute(UOW_NAME, name)
        .startSpan()

    private fun persistingSpan(name: String) = openTelemetry.getEvaTracer()
        .spanBuilder("$name-$SPAN_PERSIST")
        .setAttribute(UOW_OPERATION, SPAN_PERSIST)
        .setAttribute(UOW_NAME, name)
        .startSpan()

    private val timer = createTimer("uow.timer", "Unit of work execution time")

    private val performTimer = createTimer("uow.perform.timer", "Unit of work perform phase execution time")

    private val persistTimer = createTimer("uow.persist.timer", "Unit of work persist phase execution time")

    // unit stays a curly-brace annotation so the prometheus exporter does not suffix the metric name
    private val performAcquisitionsMetric = openTelemetry.getEvaMeter()
        .histogramBuilder("uow.perform.connection.acquisitions")
        .setDescription("Pooled connection acquisitions during unit of work perform phase")
        .setUnit("{acquisition}")
        .ofLongs()
        .setExplicitBucketBoundariesAdvice(ACQUISITION_BUCKET_BOUNDARIES)
        .build()

    private fun createTimer(name: String, description: String) = openTelemetry.getEvaMeter()
        .histogramBuilder(name)
        .setDescription(description)
        .setUnit("ns")
        .ofLongs()
        // otel default boundaries are millisecond-oriented (5 .. 10000), so every nanosecond-scale
        // observation lands in +Inf and quantiles saturate; advise boundaries covering 0.5ms .. 60s
        .setExplicitBucketBoundariesAdvice(TIMER_BUCKET_BOUNDARIES_NANOS)
        .build()

    private inline fun <T> instrumentedPerform(uowName: String, block: (ConnectionAcquisitionCounter) -> T): T {
        val acquisitions = ConnectionAcquisitionCounter()
        val start = System.nanoTime()
        return try {
            block(acquisitions)
        } finally {
            val attributes = Attributes.of(AttributeKey.stringKey(UOW_NAME), uowName)
            performTimer.record(System.nanoTime() - start, attributes)
            performAcquisitionsMetric.record(acquisitions.count(), attributes)
        }
    }

    private inline fun <T> timed(timer: LongHistogram, uowName: String, block: () -> T): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            timer.record(System.nanoTime() - start, Attributes.of(AttributeKey.stringKey(UOW_NAME), uowName))
        }
    }

    private object SpanAttributes {

        val peristenceException = AttributeKey.stringKey("com.razz.eva.persistence.PersistenceException")
        val modelIds = AttributeKey.stringArrayKey("com.razz.eva.domain.ModelId")
    }

    companion object {
        private val TIMER_BUCKET_BOUNDARIES_NANOS = listOf(
            500_000L, // 0.5ms
            1_000_000L, // 1ms
            2_500_000L,
            5_000_000L,
            10_000_000L, // 10ms
            25_000_000L,
            50_000_000L,
            75_000_000L,
            100_000_000L, // 100ms
            250_000_000L,
            500_000_000L,
            1_000_000_000L, // 1s
            2_500_000_000L,
            5_000_000_000L,
            10_000_000_000L, // 10s
            30_000_000_000L,
            60_000_000_000L, // 60s
        )
        private val ACQUISITION_BUCKET_BOUNDARIES = listOf(0L, 1L, 2L, 3L, 5L, 8L, 13L, 21L, 50L)
    }
}
