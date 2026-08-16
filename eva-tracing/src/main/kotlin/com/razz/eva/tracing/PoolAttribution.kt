package com.razz.eva.tracing

import com.razz.eva.tracing.DatabaseSpans.setServer
import io.opentelemetry.api.trace.Span

/**
 * Reports the pool a call went to.
 *
 * A transaction manager holds one and calls it as it goes to a pool. The manager is the only code that
 * knows which pool a call uses, so the manager reports it. Nothing travels back to the caller, and no
 * executor has to remember to read a value and set an attribute.
 */
fun interface PoolAttribution {

    fun record(address: String, port: Int, database: String, role: String?)

    companion object {

        /** Reports nothing. */
        val None = PoolAttribution { _, _, _, _ -> }

        /**
         * Writes the pool onto the span that is current when the manager goes to a pool. A query executor
         * makes its span current for the whole call, so that span is the one this writes to. Without a
         * current span this writes nothing.
         */
        val CurrentSpan = PoolAttribution { address, port, database, role ->
            val span = Span.current()
            if (span.isRecording) {
                span.setServer(address, port, database, role)
            }
        }
    }
}
