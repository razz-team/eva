package com.razz.eva.tracing

import io.kotest.core.spec.IsolationMode.InstancePerTest
import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.trace.SpanKind.CLIENT
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.ExceptionEventData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import java.sql.SQLException
import kotlinx.coroutines.withContext
import org.jooq.ExecuteContext

private val table = org.jooq.impl.DSL.table(org.jooq.impl.DSL.name("model_events"))
private val jooqQuery = org.jooq.impl.DSL.using(org.jooq.SQLDialect.POSTGRES).deleteQuery(table)
private val plainSql = org.jooq.impl.DSL.using(org.jooq.SQLDialect.POSTGRES)
    .resultQuery("delete from model_events")

class QueryTracingListenerProviderSpec : AnnotationSpec() {

    override fun isolationMode() = InstancePerTest

    val spanExporter = InMemorySpanExporter.create()
    val telemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(
            SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build(),
        )
        .build()
    private val listenerProvider = QueryTracingListenerProvider(telemetry, sourceQuery = null)

    @Test
    suspend fun `should name the span after the operation and the table`() {
        val listener = listenerProvider.provide()
        val rootSpan = telemetry.tracerProvider.get("JOOQ").spanBuilder("root").startSpan()
        val sqlContext = mockk<ExecuteContext> {
            every { sql() } returns "delete from model_events"
            every { query() } returns jooqQuery
        }
        withContext(rootSpan.asContextElement()) {
            listener.executeStart(sqlContext)
            listener.executeEnd(sqlContext)
        }
        val span = spanExporter.finishedSpanItems.single()
        span.name shouldBe "DELETE model_events"
        span.attributes.get(stringKey("db.operation.name")) shouldBe "DELETE"
        span.attributes.get(stringKey("db.collection.name")) shouldBe "model_events"
    }

    @Test
    suspend fun `should name the span after the query the rendered sql came from`() {
        val listener = QueryTracingListenerProvider(telemetry, sourceQuery = jooqQuery).provide()
        val sqlContext = mockk<ExecuteContext> {
            every { sql() } returns "delete from model_events"
            every { query() } returns plainSql
        }
        withContext(telemetry.tracerProvider.get("JOOQ").spanBuilder("root").startSpan().asContextElement()) {
            listener.executeStart(sqlContext)
            listener.executeEnd(sqlContext)
        }
        val span = spanExporter.finishedSpanItems.single()
        span.name shouldBe "DELETE model_events"
        span.attributes.get(stringKey("db.operation.name")) shouldBe "DELETE"
        span.attributes.get(stringKey("db.collection.name")) shouldBe "model_events"
    }

    @Test
    suspend fun `should name a plain sql statement after the operation alone`() {
        val listener = listenerProvider.provide()
        val sqlContext = mockk<ExecuteContext> {
            every { sql() } returns "delete from model_events"
            every { query() } returns plainSql
        }
        withContext(telemetry.tracerProvider.get("JOOQ").spanBuilder("root").startSpan().asContextElement()) {
            listener.executeStart(sqlContext)
            listener.executeEnd(sqlContext)
        }
        val span = spanExporter.finishedSpanItems.single()
        span.name shouldBe "QUERY"
        span.attributes.get(stringKey("db.collection.name")) shouldBe null
    }

    @Test
    suspend fun `should not record a span for a sampled out trace`() {
        val dropped = OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOff())
                    .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                    .build(),
            )
            .build()
        val listener = listenerProvider.provide()
        val sqlContext = mockk<ExecuteContext>()
        withContext(dropped.tracerProvider.get("JOOQ").spanBuilder("root").startSpan().asContextElement()) {
            listener.executeStart(sqlContext)
            listener.executeEnd(sqlContext)
        }
        spanExporter.finishedSpanItems.shouldBeEmpty()
    }

    @Test
    suspend fun `should cap an oversized statement and report its length`() {
        val listener = QueryTracingListenerProvider(telemetry, sourceQuery = null, maxStatementLength = 16).provide()
        val long = "delete from model_events where id in (" + "?, ".repeat(50) + ")"
        val sqlContext = mockk<ExecuteContext> {
            every { sql() } returns long
            every { query() } returns jooqQuery
        }
        withContext(telemetry.tracerProvider.get("JOOQ").spanBuilder("root").startSpan().asContextElement()) {
            listener.executeStart(sqlContext)
            listener.executeEnd(sqlContext)
        }
        val span = spanExporter.finishedSpanItems.single()
        span.attributes.get(stringKey("db.statement")) shouldBe long.take(16)
        span.attributes.get(io.opentelemetry.api.common.AttributeKey.longKey("db.statement.length")) shouldBe
            long.length.toLong()
    }

    @Test
    suspend fun `should end span when query is completed`() {
        // given
        val listener = listenerProvider.provide()
        val rootSpan = telemetry.tracerProvider.get("JOOQ")
            .spanBuilder("root")
            .startSpan()
        val sqlContext = mockk<ExecuteContext> {
            every { sql() } returns "SELECT * FROM table"
            every { query() } returns jooqQuery
        }

        withContext(rootSpan.asContextElement()) {
            // when
            listener.executeStart(sqlContext)
            listener.executeEnd(sqlContext)

            // then
            spanExporter.finishedSpanItems shouldHaveSize 1
            val querySpan = spanExporter.finishedSpanItems.first()

            querySpan.attributes[stringKey("db.system")] shouldBe "postgresql"
            querySpan.attributes[stringKey("db.statement")] shouldBe "SELECT * FROM table"

            querySpan.kind shouldBe CLIENT
        }
    }

    @Test
    suspend fun `should set exception to span when query failed`() {
        // given
        val listener = listenerProvider.provide()
        val rootSpan = telemetry.tracerProvider.get("JOOQ")
            .spanBuilder("root")
            .startSpan()
        val exception = SQLException("some sql error")
        val sqlContext = mockk<ExecuteContext> {
            every { sql() } returns "SELECT * FROM table"
            every { query() } returns jooqQuery
            every { sqlException() } returns exception
        }

        withContext(rootSpan.asContextElement()) {
            // when
            listener.executeStart(sqlContext)
            listener.exception(sqlContext)

            // then
            spanExporter.finishedSpanItems shouldHaveSize 1
            val querySpan = spanExporter.finishedSpanItems.first()

            querySpan.attributes[stringKey("db.system")] shouldBe "postgresql"
            querySpan.attributes[stringKey("db.statement")] shouldBe "SELECT * FROM table"

            querySpan.events shouldHaveSize 1
            (querySpan.events.first() as ExceptionEventData).exception shouldBe exception

            querySpan.kind shouldBe CLIENT
        }
    }

    @Test
    fun `there is no root span`() {
        // given
        val listener = listenerProvider.provide()
        val sqlContext = mockk<ExecuteContext> {
            every { sql() } returns "SELECT * FROM table"
            every { query() } returns jooqQuery
        }

        // when
        listener.executeStart(sqlContext)
        listener.executeEnd(sqlContext)

        // then
        spanExporter.finishedSpanItems shouldHaveSize 0
    }
}
