package com.razz.eva.tracing

import io.opentracing.Tracer
import kotlin.coroutines.coroutineContext

/**
 * Extracts active span from the coroutine context and restores it into the `tracer`'s ThreadLocal variable,
 * so it can be accessed later downstream in non-suspending functions.
 *
 * After the execution of the block, the original active span is restored.
 *
 * `block` is made non-suspending on purpose, to make sure that ThreadLocal-stored spans are not lost on context switch.
 */
suspend fun <T> Tracer.withRestoredThreadLocalSpan(block: () -> T): T {
    val coroutineSpan = coroutineContext[ActiveSpanElement]
    val scope = coroutineSpan?.let { activateSpan(it.span) }

    return scope.use { block() }
}
