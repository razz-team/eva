package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.Principal
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.persistence.PrimaryConnectionRequiredFlag
import com.razz.eva.tracing.use
import com.razz.eva.uow.UnitOfWorkExecutor.ClassToUow
import io.opentelemetry.api.OpenTelemetry
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
        it.value.single().uowFactory
    }

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        principal: PRINCIPAL,
        uowFactory: () -> UOW,
        params: InstantiationContext.() -> PARAMS
    ): RESULT where PRINCIPAL : Principal<*>,
          PARAMS : UowParams<PARAMS>,
          UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        lateinit var executorSpan: Span
        lateinit var name: String
        try {
            var currentAttempt = 0
            while (true) {
                val uow = uowFactory()
                if (currentAttempt == 0) {
                    name = uow.name()
                    executorSpan = executorSpan()
                }
                val constructedParams = params(InstantiationContext(currentAttempt))
                val changes = withContext(PrimaryConnectionRequiredFlag + executorSpan.asContextElement()) {
                    uowSpan(name).use {
                        uow.tryPerform(principal, constructedParams)
                    }
                }
                val persisted = try {
                    withContext(executorSpan.asContextElement()) {
                        persisting.persist(
                            uowName = uow.name(),
                            params = constructedParams,
                            principal = principal,
                            changes = changes.toPersist,
                            clock = uow.clock(),
                            uowSupportsOutOfOrderPersisting = uow.configuration().supportsOutOfOrderPersisting
                        )
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
            executorSpan.recordException(ex)
            throw ex
        } finally {
            executorSpan.end()
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

    private fun executorSpan() = openTelemetry.getTracer("eva")
        .spanBuilder(SPAN_SERVICE)
        .setAttribute(SPAN_SERVICE_KEY, SPAN_SERVICE)
        .startSpan()

    private fun uowSpan(uowName: String) = openTelemetry.getTracer("eva")
        .spanBuilder(uowName)
        .setAttribute(SPAN_SERVICE_KEY, SPAN_SERVICE)
        .startSpan()

    companion object {
        private const val SPAN_SERVICE = "UowExecutor"
        private const val SPAN_SERVICE_KEY = "service.name"
    }
}

class UowFactoryNotFoundException(uowClass: KClass<*>) :
    IllegalStateException("There is no configured factory to create ${uowClass.simpleName}")
