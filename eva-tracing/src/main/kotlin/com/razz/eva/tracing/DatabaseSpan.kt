package com.razz.eva.tracing

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind.CLIENT
import io.opentelemetry.context.Context
import org.jooq.Query

/** Default cap on the `db.statement` attribute. */
const val MAX_STATEMENT_LENGTH: Int = 8 * 1024

/**
 * Whether anything is being recorded. A caller checks this before doing the work a span needs, which keeps
 * queries outside a request, a job or a consumer untraced, module initialisation and migrations among them.
 * A sampled out trace is not recording either.
 */
fun tracingDatabaseQueries(): Boolean = Span.fromContextOrNull(Context.current())?.isRecording == true

/**
 * A span for one database query, named and attributed by the semantic conventions for database clients.
 *
 * Both the jOOQ execute listener and the vertx executor build their spans here, so the two paths cannot
 * drift on a name or an attribute.
 */
fun OpenTelemetry.databaseSpan(
    tracerName: String,
    jooqQuery: Query?,
    statement: String,
    maxStatementLength: Int = MAX_STATEMENT_LENGTH,
): Span {
    val span = getTracer(tracerName)
        .spanBuilder(QueryNaming.spanName(jooqQuery))
        .setAttribute("db.system", "postgresql")
        .setAttribute("db.operation.name", QueryNaming.operationName(jooqQuery))
        .setAttribute("db.statement", statement.take(maxStatementLength))
        .setSpanKind(CLIENT)
        .startSpan()
    QueryNaming.queryTarget(jooqQuery)?.let { span.setAttribute("db.collection.name", it) }
    if (statement.length > maxStatementLength) {
        span.setAttribute("db.statement.length", statement.length.toLong())
    }
    return span
}
