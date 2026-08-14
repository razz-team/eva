package com.razz.eva.persistence.jdbc.executor

import com.razz.eva.persistence.DbEndpoint
import com.razz.eva.persistence.PrimaryConnectionRequiredFlag
import com.razz.eva.persistence.jdbc.JdbcConnectionProvider
import com.razz.eva.persistence.jdbc.JdbcTransactionManager
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
import kotlinx.coroutines.withContext
import org.jooq.Record
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockResult

private val orders = object : TableImpl<Record>(DSL.name("payment_order")) {
    val ID = createField(DSL.name("id"), SQLDataType.UUID)!!
}

private val primaryEndpoint = DbEndpoint("pgcat-primary", 6432, "eva")
private val replicaEndpoint = DbEndpoint("pgcat-replica", 6432, "eva")

/**
 * That the jdbc executor emits a span, with the pool the transaction manager chose. The vertx module has
 * the same coverage; without this one the jdbc path was asserted by nothing at all.
 */
class JdbcQueryExecutorTracingSpec : ShouldSpec({

    val dslContext = DSL.using(POSTGRES)
    val spanExporter = InMemorySpanExporter.create()
    val telemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(
            SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build(),
        )
        .build()

    val primaryProvider = mockk<JdbcConnectionProvider>(relaxed = true) {
        every { endpoint } returns primaryEndpoint
    }
    val replicaProvider = mockk<JdbcConnectionProvider>(relaxed = true) {
        every { endpoint } returns replicaEndpoint
    }
    val transactionManager = spyk(JdbcTransactionManager(primaryProvider, replicaProvider))
    val executor = JdbcQueryExecutor(transactionManager, telemetry)

    val connection = MockConnection { arrayOf(MockResult(0, dslContext.newResult(orders))) }
    coEvery { primaryProvider.acquire() } coAnswers { connection }
    coEvery { replicaProvider.acquire() } coAnswers { connection }

    fun root() = telemetry.tracerProvider.get("test").spanBuilder("root").startSpan()

    suspend fun select(primaryRequired: Boolean = false) {
        if (primaryRequired) {
            withContext(PrimaryConnectionRequiredFlag) {
                executor.executeSelect(dslContext, dslContext.selectFrom(orders), orders)
            }
        } else {
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

    should("emit nothing outside a request") {
        spanExporter.reset()
        select()
        spanExporter.finishedSpanItems.shouldBeEmpty()
    }
})
