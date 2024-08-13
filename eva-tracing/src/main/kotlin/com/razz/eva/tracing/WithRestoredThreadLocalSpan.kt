package com.razz.eva.tracing

import io.opentracing.Span
import io.opentracing.Tracer
import kotlin.coroutines.coroutineContext

/**
 * Extracts active span from the coroutine context and restores it into the `tracer`'s ThreadLocal variable,
 * so it can be accessed later downstream in non-suspending functions.
 *
 * After the execution of the block, the original active span is restored.
 *
 * `block` is specifically made non-suspending to make use of ThreadLocal-stored spans.
 */
suspend fun <T> Tracer.withRestoredThreadLocalSpan(block: () -> T): T {
    val activeSpan: Span? = activeSpan()

    val coroutineSpan = coroutineContext[ActiveSpanElement]
    if (coroutineSpan != null) {
        activateSpan(coroutineSpan.span)
    }

    try {
        return block()
    } finally {
        if (coroutineSpan != null) {
            activateSpan(activeSpan)
        }
    }
}
