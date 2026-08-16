package com.razz.eva.persistence.vertx.executor

import com.razz.eva.persistence.DbEndpoint
import com.razz.eva.persistence.PrimaryConnectionRequiredFlag
import com.razz.eva.persistence.vertx.PgPoolConnectionProvider
import com.razz.eva.persistence.vertx.VertxTransactionManager
import com.razz.eva.tracing.PoolAttribution
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.opentelemetry.api.common.AttributeKey.longKey
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.trace.SpanKind.CLIENT
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.vertx.core.Future.succeededFuture
import io.vertx.pgclient.PgConnection
import io.vertx.sqlclient.PreparedQuery
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.impl.ListTuple
import org.jooq.Record
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl
import java.util.function.Function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val orders = object : TableImpl<Record>(DSL.name("payment_order")) {
    val ID = createField(DSL.name("id"), SQLDataType.UUID)!!
}

private val primaryEndpoint = DbEndpoint("pgcat-primary", 6432, "eva")
private val replicaEndpoint = DbEndpoint("pgcat-replica", 6432, "eva")

/**
 * That the executor emits a span at all, with the pool the transaction manager actually chose. The
 * DatabaseSpans spec only covers a span the test itself builds, which proves nothing about wiring.
 */
class VertxQueryExecutorTracingSpec : ShouldSpec({

    val dslContext = DSL.using(POSTGRES)
    val spanExporter = InMemorySpanExporter.create()
    val telemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(
            SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build(),
        )
        .build()

    val primaryProvider = mockk<PgPoolConnectionProvider>(relaxed = true) {
        every { endpoint } returns primaryEndpoint
    }
    val replicaProvider = mockk<PgPoolConnectionProvider>(relaxed = true) {
        every { endpoint } returns replicaEndpoint
    }
    val transactionManager = spyk(VertxTransactionManager(
        primaryProvider,
        replicaProvider,
        attribution = PoolAttribution.CurrentSpan,
    ))
    val executor = VertxQueryExecutor(transactionManager, telemetry)

    val preparedQueryMock = mockk<PreparedQuery<RowSet<Row>>> {
        every { mapping(any<Function<Row, Any>>()) } answers {
            mockk {
                every { execute(any<ListTuple>()) } returns succeededFuture(
                    mockk {
                        every { iterator() } answers { mockk { every { hasNext() } returns false } }
                        every { size() } returns 0
                    },
                )
            }
        }
    }
    val connection = mockk<PgConnection>(relaxed = true) {
        every { preparedQuery(any()) } answers { preparedQueryMock }
    }
    coEvery { primaryProvider.acquire() } coAnswers { connection }
    coEvery { replicaProvider.acquire() } coAnswers { connection }

    fun root() = telemetry.tracerProvider.get("test").spanBuilder("root").startSpan()

    suspend fun select(primaryRequired: Boolean = false) {
        val ctx = if (primaryRequired) Dispatchers.IO + PrimaryConnectionRequiredFlag else Dispatchers.IO
        withContext(ctx) {
            executor.executeSelect(dslContext, dslContext.selectFrom(orders), orders)
        }
    }

    should("emit a span named after the operation and the table, carrying the pool that served it") {
        spanExporter.reset()
        val root = root()
        withContext(root.asContextElement()) { select() }
        root.end()

        val span = spanExporter.finishedSpanItems.single { it.name != "root" }
        span.name shouldBe "SELECT payment_order"
        span.kind shouldBe CLIENT
        span.attributes.get(stringKey("db.collection.name")) shouldBe "payment_order"
        span.attributes.get(stringKey("server.address")) shouldBe "pgcat-replica"
        span.attributes.get(longKey("server.port")) shouldBe 6432L
        span.attributes.get(stringKey("db.namespace")) shouldBe "eva"
        span.attributes.get(stringKey("db.pool.role")) shouldBe "REPLICA"
    }

    should("report the primary when the caller demands it") {
        spanExporter.reset()
        val root = root()
        withContext(root.asContextElement()) { select(primaryRequired = true) }
        root.end()

        val span = spanExporter.finishedSpanItems.single { it.name != "root" }
        span.attributes.get(stringKey("server.address")) shouldBe "pgcat-primary"
        span.attributes.get(stringKey("db.pool.role")) shouldBe "PRIMARY"
    }

    should("parent the span under the caller so the query is attributable") {
        spanExporter.reset()
        val root = root()
        withContext(root.asContextElement()) { select() }
        root.end()

        val span = spanExporter.finishedSpanItems.single { it.name != "root" }
        span.parentSpanId shouldBe root.spanContext.spanId
    }

    should("emit nothing outside a request") {
        spanExporter.reset()
        select()
        spanExporter.finishedSpanItems.shouldBeEmpty()
    }
})
