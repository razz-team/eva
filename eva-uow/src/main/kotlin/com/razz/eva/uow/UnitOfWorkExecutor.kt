package com.razz.eva.uow

import com.razz.eva.domain.Principal
import com.razz.eva.metrics.timerBuilder
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.persistence.PrimaryConnectionRequiredFlag
import com.razz.eva.tracing.ActiveSpanElement
import com.razz.eva.tracing.Tracing
import com.razz.eva.tracing.Tracing.PERFORM
import com.razz.eva.tracing.Tracing.PERSIST
import com.razz.eva.uow.UnitOfWorkExecutor.ClassToUow
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.tag.Tags.ERROR
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit.NANOSECONDS
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass

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
    private val tracer: Tracer,
    private val meterRegistry: MeterRegistry
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
        params: () -> PARAMS
    ): RESULT where PRINCIPAL : Principal<*>,
                    PARAMS : UowParams<PARAMS>,
                    UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *> {
        val startTime = System.nanoTime()
        val activeSpan = coroutineContext[ActiveSpanElement]?.span
        if (activeSpan == null) {
            logger.debug { "No active span found in uow context, check tracing configuration" }
        }
        lateinit var timer: Timer
        lateinit var uowSpan: Span
        lateinit var name: String
        try {
            var currentAttempt = 0
            while (true) {
                val uow = uowFactory()
                if (currentAttempt == 0) {
                    name = uow.name()
                    timer = createTimer(name)
                    uowSpan = tracer.buildSpan(name).asChildOf(activeSpan).withTag(Tracing.Tags.UOW_NAME, name).start()
                }
                val constructedParams = params()
                val performSpan = buildPerformSpan(name, uowSpan)
                val changes = withContext(ActiveSpanElement(performSpan) + PrimaryConnectionRequiredFlag) {
                    try {
                        uow.tryPerform(principal, constructedParams)
                    } catch (e: Exception) {
                        performSpan.finishWithError(e)
                        throw e
                    }
                }
                performSpan.finish()
                val persistSpan = buildPersistSpan(name, uowSpan)
                try {
                    persisting.persist(
                        uowName = uow.name(),
                        params = constructedParams,
                        principal = principal,
                        changes = changes.toPersist,
                        clock = uow.clock(),
                        uowSupportsOutOfOrderPersisting = uow.configuration().supportsOutOfOrderPersisting
                    )
                } catch (e: PersistenceException) {
                    val config = uow.configuration()
                    uowSpan.log("persistence-exception")
                    if (config.retry.shouldRetry(currentAttempt, e)) {
                        currentAttempt += 1
                        logger.warn { "Retrying UnitOfWork: ${uow.name()}. Attempt: $currentAttempt" }
                        continue
                    }
                    persistSpan.finishWithError(e)
                    return uow.onFailure(constructedParams, e)
                }
                persistSpan.finish()
                return changes.result
            }
        } finally {
            val endTime = System.nanoTime()
            val elapsedTime = endTime - startTime
            timer.record(elapsedTime, NANOSECONDS)
            uowSpan.finish()
        }
    }

    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        target: KClass<UOW>,
        principal: PRINCIPAL,
        params: () -> PARAMS
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

    private fun createTimer(name: String) = timerBuilder("UnitsOfWorkExecutor")
        .tag("UnitOfWork", name)
        .register(meterRegistry)

    @Suppress("UNCHECKED_CAST")
    private fun <PRINCIPAL : Principal<*>, PARAMS, RESULT, UOW : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, *>>
    create(target: KClass<UOW>): UOW {
        val factory = classToFactory[target] ?: throw UowFactoryNotFoundException(target)
        return (factory as () -> UOW)()
    }

    private fun buildPersistSpan(name: String, uowSpan: Span?) = tracer
        .buildSpan("$name-$PERSIST")
        .asChildOf(uowSpan)
        .withTag(Tracing.Tags.UOW_NAME, name)
        .withTag(Tracing.Tags.UOW_OPERATION, PERSIST)
        .start()

    private fun buildPerformSpan(name: String, uowSpan: Span?) = tracer
        .buildSpan("$name-$PERFORM")
        .asChildOf(uowSpan)
        .withTag(Tracing.Tags.UOW_NAME, name)
        .withTag(Tracing.Tags.UOW_OPERATION, PERFORM)
        .start()

    private fun Span.finishWithError(e: Exception) = this
        .setTag(ERROR, true)
        .log(mapOf("stacktrace" to StringWriter().apply { e.printStackTrace(PrintWriter(this)) }))
        .finish()
}

class UowFactoryNotFoundException(uowClass: KClass<*>) :
    IllegalStateException("There is no configured factory to create ${uowClass.simpleName}")
