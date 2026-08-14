package com.razz.eva.tracing

import com.razz.eva.tracing.DatabaseSpans.setServer
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
import io.opentelemetry.sdk.trace.samplers.Sampler
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
    suspend fun `should not trace under a sampled out trace`() {
        val neverSampled = OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .setSampler(Sampler.alwaysOff())
                    .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                    .build(),
            )
            .build()
        val dropped = neverSampled.tracerProvider.get("test").spanBuilder("root").startSpan()
        withContext(dropped.asContextElement()) {
            DatabaseSpans.tracing() shouldBe false
        }
    }

    @Test
    suspend fun `should name the span after the operation and the table`() {
        withContext(rootSpan().asContextElement()) {
            DatabaseSpans.querySpan(telemetry, "SELECT", "model_events", "select * from model_events").end()
        }
        val span = spanExporter.finishedSpanItems.single { it.name != "root" }
        span.name shouldBe "SELECT model_events"
        span.kind shouldBe CLIENT
        span.attributes.get(stringKey("db.system")) shouldBe "postgresql"
        span.attributes.get(stringKey("db.operation.name")) shouldBe "SELECT"
        span.attributes.get(stringKey("db.collection.name")) shouldBe "model_events"
        span.attributes.get(stringKey("db.statement")) shouldBe "select * from model_events"
    }

    @Test
    suspend fun `should fall back to the operation alone without a table`() {
        withContext(rootSpan().asContextElement()) {
            DatabaseSpans.querySpan(telemetry, "DELETE", null, "delete from idempotency_key").end()
        }
        val spans = spanExporter.finishedSpanItems.filter { it.name != "root" }
        spans shouldHaveSize 1
        spans.single().name shouldBe "DELETE"
        spans.single().attributes.get(stringKey("db.collection.name")) shouldBe null
    }

    @Test
    suspend fun `should leave the server unset until a pool is known`() {
        withContext(rootSpan().asContextElement()) {
            DatabaseSpans.querySpan(telemetry, "SELECT", "payment_order", "select 1").end()
        }
        val span = spanExporter.finishedSpanItems.single { it.name != "root" }
        span.attributes.get(stringKey("server.address")) shouldBe null
        span.attributes.get(stringKey("db.namespace")) shouldBe null
        span.attributes.get(stringKey("db.pool.role")) shouldBe null
    }

    @Test
    suspend fun `should record the pool that served the call`() {
        withContext(rootSpan().asContextElement()) {
            val span = DatabaseSpans.querySpan(telemetry, "SELECT", "payment_order", "select 1")
            span.setServer("pgcat-replica", 6432, "s2p", "REPLICA")
            span.end()
        }
        val span = spanExporter.finishedSpanItems.single { it.name != "root" }
        span.attributes.get(stringKey("server.address")) shouldBe "pgcat-replica"
        span.attributes.get(longKey("server.port")) shouldBe 6432L
        span.attributes.get(stringKey("db.namespace")) shouldBe "s2p"
        span.attributes.get(stringKey("db.pool.role")) shouldBe "REPLICA"
    }
}
