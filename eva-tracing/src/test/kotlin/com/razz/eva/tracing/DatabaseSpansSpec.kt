package com.razz.eva.tracing

import io.kotest.core.spec.IsolationMode.InstancePerTest
import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.common.AttributeKey.longKey
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.trace.SpanKind.CLIENT
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.withContext

class DatabaseSpansSpec : AnnotationSpec() {

    override fun isolationMode() = InstancePerTest

    private val spanExporter = InMemorySpanExporter.create()
    private val telemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(
            SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build(),
        )
        .build()

    private fun rootSpan() = telemetry.tracerProvider.get("test").spanBuilder("root").startSpan()

    @Test
    suspend fun `should not trace outside a request`() {
        DatabaseSpans.tracing() shouldBe false
    }

    @Test
    suspend fun `should trace inside a request`() {
        withContext(rootSpan().asContextElement()) {
            DatabaseSpans.tracing() shouldBe true
        }
    }

    @Test
    suspend fun `should name the span after the operation and the table`() {
        withContext(rootSpan().asContextElement()) {
            DatabaseSpans.querySpan(
                openTelemetry = telemetry,
                operation = "SELECT",
                target = "model_events",
                sql = "select * from model_events",
                address = "pgcat-replica",
                port = 6432,
                database = "s2p",
            ).end()
        }
        val span = spanExporter.finishedSpanItems.single { it.name != "root" }
        span.name shouldBe "SELECT model_events"
        span.kind shouldBe CLIENT
        span.attributes.get(stringKey("db.system")) shouldBe "postgresql"
        span.attributes.get(stringKey("db.namespace")) shouldBe "s2p"
        span.attributes.get(stringKey("db.operation.name")) shouldBe "SELECT"
        span.attributes.get(stringKey("db.collection.name")) shouldBe "model_events"
        span.attributes.get(stringKey("db.statement")) shouldBe "select * from model_events"
        span.attributes.get(stringKey("server.address")) shouldBe "pgcat-replica"
        span.attributes.get(longKey("server.port")) shouldBe 6432L
    }

    @Test
    suspend fun `should fall back to the operation alone without a table`() {
        withContext(rootSpan().asContextElement()) {
            DatabaseSpans.querySpan(
                openTelemetry = telemetry,
                operation = "DELETE",
                target = null,
                sql = "delete from idempotency_key",
                address = "pgcat-primary",
                port = 6432,
                database = "s2p",
            ).end()
        }
        val spans = spanExporter.finishedSpanItems.filter { it.name != "root" }
        spans shouldHaveSize 1
        spans.single().name shouldBe "DELETE"
        spans.single().attributes.get(stringKey("db.collection.name")) shouldBe null
    }
}
