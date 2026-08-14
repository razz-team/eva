package com.razz.eva.tracing

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind.CLIENT
import io.opentelemetry.context.Context

/**
 * Spans for database client calls, following the OpenTelemetry semantic conventions: named
 * `{db.operation.name} {target}` so span metrics separate a select on one table from an insert on another,
 * and carrying the address of the pool that served the call.
 *
 * A span covers the whole call the caller waited on, connection acquisition and row fetching included,
 * rather than statement execution alone, so a service starved of connections shows slow spans here.
 */
object DatabaseSpans {

    private const val TRACER = "eva-persistence"

    /**
     * Whether anything is being recorded right now. Callers check this before doing the work a span needs,
     * which keeps queries outside a request, job or consumer (module initialisation and migrations, for
     * instance) untraced. `isRecording` rather than mere presence, so a sampled out trace does not pay for
     * attributes nobody will read.
     */
    fun tracing(): Boolean = Span.fromContextOrNull(Context.current())?.isRecording ?: false

    fun querySpan(
        openTelemetry: OpenTelemetry,
        operation: String,
        target: String?,
        sql: String,
    ): Span {
        val builder = openTelemetry.getTracer(TRACER)
            .spanBuilder(if (target == null) operation else "$operation $target")
            .setSpanKind(CLIENT)
            .setAttribute("db.system", "postgresql")
            .setAttribute("db.operation.name", operation)
            // Kept as db.statement, the name already in use, rather than renamed to the newer
            // db.query.text, so existing trace queries keep working.
            .setAttribute("db.statement", sql)
        if (target != null) {
            builder.setAttribute("db.collection.name", target)
        }
        return builder.startSpan()
    }

    /**
     * The pool that served the call, recorded once it is known rather than predicted at span start. Left
     * unset when no pool was reached, since an absent address is honest where a guessed one is not. A pool
     * the manager cannot name keeps its address and omits the role only.
     */
    fun Span.setServer(address: String, port: Int, database: String, role: String?) {
        setAttribute("server.address", address)
        setAttribute("server.port", port.toLong())
        setAttribute("db.namespace", database)
        if (role != null) {
            setAttribute("db.pool.role", role)
        }
    }
}
