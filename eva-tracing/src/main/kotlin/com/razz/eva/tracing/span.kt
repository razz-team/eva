package com.razz.eva.tracing

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode.ERROR
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

suspend fun <R> Span?.use(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    block: suspend () -> R
): R {
    return if (this == null) {
        block()
    } else {
        try {
            withContext(coroutineContext + this.asContextElement()) {
                block()
            }
        } catch (ex: Exception) {
            recordException(ex)
            setStatus(ERROR)
            throw ex
        } finally {
            end()
        }
    }
}
