package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.Principal
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.persistence.PrimaryConnectionRequiredFlag
import com.razz.eva.tracing.use
import com.razz.eva.uow.UnitOfWorkExecutor.ClassToUow
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import kotlin.reflect.KClass

data class InstantiationContext internal constructor(
    internal val attempt: Int,
)

infix fun <PRINCIPAL, PARAMS, RESULT, UOW> KClass<UOW>.withFactory(
    factory: () -> UOW
) where PRINCIPAL : Principal<*>,
      PARAMS : UowParams<PARAMS>,
      UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *>,
      RESULT : Any =
    ClassToUow(this, factory)

class UnitOfWorkExecutor(
    factories: List<ClassToUow<*, *, *, *>>,
    private val persisting: Persisting,
    private val openTelemetry: OpenTelemetry,
) {

    class ClassToUow<PRINCIPAL, PARAMS, RESULT, UOW> internal constructor(
        internal val uowClass: KClass<UOW>,
        internal val uowFactory: () -> UOW
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
        uowFactory: () -> UOW,
        params: InstantiationContext.() -> PARAMS
    ): RESULT where PRINCIPAL : Principal<*>,
          PARAMS : UowParams<PARAMS>,
          UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        val startTime = System.nanoTime()
        val timer = createTimer()

        lateinit var uowSpan: Span
        lateinit var name: String
        try {
            var currentAttempt = 0
            while (true) {
                if (currentAttempt == 0) {
                    uowSpan = uowSpan()
                }
                val uow = uowFactory()
                if (currentAttempt == 0) {
                    name = uow.name()
                    uowSpan.updateName(name)
                    uowSpan.setAttribute(UOW_NAME, name)
                }
                val constructedParams = params(InstantiationContext(currentAttempt))
                val changes = withContext(PrimaryConnectionRequiredFlag + uowSpan.asContextElement()) {
                    performingSpan(name).use {
                        uow.tryPerform(principal, constructedParams)
                    }
                }
                uowSpan.setAttribute(
                    MODEL_ID,
                    changes.toPersist.map { it.id.stringValue() }
                )
                val persisted = try {
                    withContext(uowSpan.asContextElement()) {
                        persistingSpan(name).use {
                            persisting.persist(
                                uowName = uow.name(),
                                params = constructedParams,
                                principal = principal,
                                changes = changes.toPersist,
                                clock = uow.clock(),
                                uowSupportsOutOfOrderPersisting = uow.configuration().supportsOutOfOrderPersisting
                            )
                        }
                    }
                } catch (e: PersistenceException) {
                    val config = uow.configuration()
                    if (config.retry.shouldRetry(currentAttempt, e)) {
                        currentAttempt += 1
                        logger.warn { "Retrying UnitOfWork: ${uow.name()}. Attempt: $currentAttempt" }
                        continue
                    }
                    return uow.onFailure(constructedParams, e)
                }
                return if (uow.configuration().returnRoundtrippedModels) result(changes, persisted) else changes.result
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

    private fun <RESULT> result(
        changes: Changes<RESULT>,
        persisted: List<Model<*, *>>,
    ) = when (val result = changes.result) {
        is Model<*, *> -> {
            // don't try to find persisted data for returned values such as `notChanged(model)`
            if (changes.toPersist.any { it !is Noop && it.id == result.id() }) {
                @Suppress("UNCHECKED_CAST")
                val roundtripped = persisted.singleOrNull { it.id() == result.id() } as? RESULT
                if (roundtripped == null) logger.warn {
                    "Unable to find returned model [${result.id()}] in persisted changes"
                }
                roundtripped ?: result
            } else result
        }
        is Collection<*> -> {
            val models = result.filterIsInstance<Model<*, *>>()
            if (models.isEmpty()) result
            else {
                val toPersist = changes.toPersist.mapNotNull { if (it is Noop) null else it.id }.toSet()
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
        params: InstantiationContext.() -> PARAMS
    ): RESULT where PRINCIPAL : Principal<*>,
          PARAMS : UowParams<PARAMS>,
          UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        return execute(principal, { create(target) }, params)
    }

    private suspend fun Retry?.shouldRetry(currentAttempt: Int, ex: PersistenceException): Boolean =
        this?.getNextDelay(currentAttempt, ex)?.let {
            delay(it.toMillis())
            true
        } ?: false

    @Suppress("UNCHECKED_CAST")
    private fun <PRINCIPAL : Principal<*>, PARAMS, RESULT, UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *>>
    create(target: KClass<UOW>): UOW {
        val factory = classToFactory[target] ?: throw UowFactoryNotFoundException(target)
        return (factory as () -> UOW)()
    }

    private fun uowSpan() = openTelemetry.getTracer("eva")
        .spanBuilder("Uow")
        .startSpan()

    private fun performingSpan(name: String) = openTelemetry.getTracer("eva")
        .spanBuilder("$name-$SPAN_PERFORM")
        .setAttribute(UOW_OPERATION, SPAN_PERFORM)
        .setAttribute(UOW_NAME, name)
        .startSpan()

    private fun persistingSpan(name: String) = openTelemetry.getTracer("eva")
        .spanBuilder("$name-$SPAN_PERSIST")
        .setAttribute(UOW_OPERATION, SPAN_PERSIST)
        .setAttribute(UOW_NAME, name)
        .startSpan()

    private fun createTimer() = openTelemetry.getMeter("eva")
        .histogramBuilder("uow.timer")
        .setDescription("Unit of work execution time")
        .setUnit("ns")
        .ofLongs()
        .build()

    companion object {
        private const val SPAN_PERSIST = "persist"
        private const val SPAN_PERFORM = "perform"
        private const val UOW_OPERATION = "uow.operation"
        private const val UOW_NAME = "uow.name"
        private val MODEL_ID = AttributeKey.stringArrayKey("model.id")
    }
}

class UowFactoryNotFoundException(uowClass: KClass<*>) :
    IllegalStateException("There is no configured factory to create ${uowClass.simpleName}")
