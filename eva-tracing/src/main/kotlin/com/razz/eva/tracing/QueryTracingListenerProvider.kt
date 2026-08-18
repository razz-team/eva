package com.razz.eva.tracing

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode.ERROR
import org.jooq.ExecuteContext
import org.jooq.ExecuteListener
import org.jooq.ExecuteListenerProvider
import org.jooq.Query

class QueryTracingListenerProvider(
    private val openTelemetry: OpenTelemetry,
    /**
     * The typed query the statement came from. An executor that renders a query to text and then runs plain
     * SQL leaves jOOQ with nothing to name a span from: [ExecuteContext.query] reports the plain SQL query,
     * which matches no QOM type, so the span reads `QUERY` and carries no table. Such an executor builds one
     * provider per statement and passes the query it started with here.
     *
     * Null leaves the naming to [ExecuteContext.query], which is what a jOOQ built statement needs.
     */
    private val sourceQuery: Query?,
    /**
     * Cap on the `db.statement` attribute. A folded multi row insert or a large `IN` list renders to a very
     * long string, and nothing downstream truncates it: the OpenTelemetry default attribute value length is
     * unlimited. A statement over the cap is cut, and `db.statement.length` reports what it was.
     *
     * Inlined bind values are part of the rendered string, so this also bounds how much of them travels.
     */
    private val maxStatementLength: Int = MAX_STATEMENT_LENGTH,
) : ExecuteListenerProvider {

    override fun provide(): ExecuteListener = TracingListener(openTelemetry, sourceQuery, maxStatementLength)

    private class TracingListener(
        private val openTelemetry: OpenTelemetry,
        private val sourceQuery: Query?,
        private val maxStatementLength: Int,
    ) : ExecuteListener {
        private var span: Span? = null

        override fun executeStart(context: ExecuteContext) {
            if (tracingDatabaseQueries()) {
                span = openTelemetry.databaseSpan(
                    tracerName = "JOOQ",
                    jooqQuery = sourceQuery ?: context.query(),
                    statement = context.sql() ?: "",
                    maxStatementLength = maxStatementLength,
                )
            }
        }

        override fun executeEnd(ctx: ExecuteContext) {
            span?.end()
        }

        override fun exception(ctx: ExecuteContext) {
            val ex = ctx.sqlException()
            if (ex != null) {
                span?.recordException(ex)
                // Without a status the span metric error rate reads zero for every failed query.
                span?.setStatus(ERROR)
                span?.end()
            }
        }
    }
}
