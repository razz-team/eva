package com.razz.eva.tracing

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind.CLIENT
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
            val rootSpan = Span.fromContextOrNull(Context.current())
            // We don't want to record queries out of requests/jobs/consumers (f.e. module init or migrations)
            if (rootSpan != null) {
                span = openTelemetry.getTracer("JOOQ")
                    .spanBuilder("PostgreSQL")
                    .setAttribute("db.system", "postgresql")
                    .setAttribute("db.statement", context.sql() ?: "")
                    .setSpanKind(CLIENT)
                    .startSpan()
            }
        }

        override fun executeEnd(ctx: ExecuteContext) {
            span?.end()
        }

        override fun exception(ctx: ExecuteContext) {
            val ex = ctx.sqlException()
            if (ex != null) {
                span?.recordException(ex)
            }
        }
    }
}
