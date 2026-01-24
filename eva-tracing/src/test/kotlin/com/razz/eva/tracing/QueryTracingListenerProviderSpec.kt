package com.razz.eva.tracing

import io.kotest.core.spec.IsolationMode.InstancePerTest
import io.kotest.core.spec.style.AnnotationSpec
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
    val listenerProvider = QueryTracingListenerProvider(telemetry)

    @Test
    suspend fun `should end span when query is completed`() {
        // given
        val listener = listenerProvider.provide()
        val rootSpan = telemetry.tracerProvider.get("JOOQ")
            .spanBuilder("root")
            .startSpan()
        val sqlContext = mockk<ExecuteContext> {
            every { sql() } returns "SELECT * FROM table"
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
        }

        // when
        listener.executeStart(sqlContext)
        listener.executeEnd(sqlContext)

        // then
        spanExporter.finishedSpanItems shouldHaveSize 0
    }
}
