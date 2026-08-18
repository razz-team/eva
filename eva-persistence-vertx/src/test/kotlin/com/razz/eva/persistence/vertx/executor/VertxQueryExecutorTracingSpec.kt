package com.razz.eva.persistence.vertx.executor

import com.razz.eva.persistence.vertx.PgPoolConnectionProvider
import com.razz.eva.persistence.vertx.VertxConnectionElement
import com.razz.eva.persistence.vertx.VertxTransactionManager
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.trace.SpanKind.CLIENT
import io.opentelemetry.api.trace.StatusCode
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.Record
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl
import java.util.function.Function

private val orders = object : TableImpl<Record>(DSL.name("payment_order")) {
    val ID = createField(DSL.name("id"), SQLDataType.UUID)!!
}

/**
 * The vertx executor does not run through jOOQ, so the jOOQ execute listener never fires for it. Before this
 * it emitted no spans, which made a service's traces depend on the executor it started with.
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

    val connectionProvider = mockk<PgPoolConnectionProvider>(relaxed = true)
    val transactionManager = spyk(VertxTransactionManager(connectionProvider, connectionProvider))
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
    coEvery { connectionProvider.acquire() } coAnswers { connection }

    fun root() = telemetry.tracerProvider.get("test").spanBuilder("root").startSpan()

    suspend fun select() {
        withContext(Dispatchers.IO + VertxConnectionElement(connection)) {
            executor.executeSelect(dslContext, dslContext.selectFrom(orders), orders)
        }
    }

    should("emit a span named after the operation and the table") {
        spanExporter.reset()
        val root = root()
        withContext(root.asContextElement()) { select() }
        root.end()

        val span = spanExporter.finishedSpanItems.single { it.name != "root" }
        span.name shouldBe "SELECT payment_order"
        span.kind shouldBe CLIENT
        span.attributes.get(stringKey("db.system")) shouldBe "postgresql"
        span.attributes.get(stringKey("db.operation.name")) shouldBe "SELECT"
        span.attributes.get(stringKey("db.collection.name")) shouldBe "payment_order"
        span.parentSpanId shouldBe root.spanContext.spanId
    }

    should("report the statement the driver receives") {
        spanExporter.reset()
        val root = root()
        withContext(root.asContextElement()) { select() }
        root.end()

        val span = spanExporter.finishedSpanItems.single { it.name != "root" }
        span.attributes.get(stringKey("db.statement")) shouldBe
            """select "payment_order"."id" from "payment_order""""
    }

    should("leave the caller span free of database attributes") {
        spanExporter.reset()
        val root = root()
        withContext(root.asContextElement()) { select() }
        root.end()

        val parent = spanExporter.finishedSpanItems.single { it.name == "root" }
        parent.attributes.get(stringKey("db.system")) shouldBe null
        parent.status.statusCode shouldBe StatusCode.UNSET
    }

    should("emit nothing outside a request") {
        spanExporter.reset()
        select()
        spanExporter.finishedSpanItems.shouldBeEmpty()
    }
})
