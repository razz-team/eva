package com.razz.eva.tracing

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind.CLIENT
import io.opentelemetry.api.trace.StatusCode.ERROR
import io.opentelemetry.context.Context
import org.jooq.ExecuteContext
import org.jooq.ExecuteListener
import org.jooq.ExecuteListenerProvider

class QueryTracingListenerProvider(
    private val openTelemetry: OpenTelemetry,
) : ExecuteListenerProvider {

    override fun provide(): ExecuteListener = TracingListener(openTelemetry)

    private class TracingListener(private val openTelemetry: OpenTelemetry) : ExecuteListener {
        private var span: Span? = null

        override fun executeStart(context: ExecuteContext) {
            // We don't want to record queries out of requests/jobs/consumers (f.e. module init or migrations),
            // and isRecording also skips a trace that sampling has already dropped.
            if (Span.fromContextOrNull(Context.current())?.isRecording == true) {
                val query = context.query()
                span = openTelemetry.getTracer("JOOQ")
                    .spanBuilder(QueryNaming.spanName(query))
                    .setAttribute("db.system", "postgresql")
                    .setAttribute("db.operation.name", QueryNaming.operationName(query))
                    .setAttribute("db.statement", context.sql() ?: "")
                    .setSpanKind(CLIENT)
                    .startSpan()
                QueryNaming.queryTarget(query)?.let { span?.setAttribute("db.collection.name", it) }
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
