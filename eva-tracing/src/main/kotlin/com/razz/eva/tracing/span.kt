package com.razz.eva.tracing

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode.ERROR
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.withContext

suspend fun <R> Span?.use(block: suspend () -> R): R {
    return if (this == null) {
        block()
    } else {
        try {
            withContext(this.asContextElement()) {
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
