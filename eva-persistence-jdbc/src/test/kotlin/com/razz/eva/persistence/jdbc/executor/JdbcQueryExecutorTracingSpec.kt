package com.razz.eva.persistence.jdbc.executor

import com.razz.eva.persistence.jdbc.JdbcConnectionProvider
import com.razz.eva.persistence.jdbc.JdbcTransactionManager
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
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

/**
 * This executor renders a query and then runs plain SQL, so the jOOQ execute listener sees a plain SQL query
 * and no query object it can name a span from. Without the query the executor started with, every span reads
 * `QUERY` and carries no table.
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

    val connection = MockConnection { arrayOf(MockResult(0, dslContext.newResult(orders))) }
    val connectionProvider = mockk<JdbcConnectionProvider>(relaxed = true) {
        coEvery { acquire() } returns connection
    }
    val transactionManager = JdbcTransactionManager(connectionProvider, connectionProvider)
    val executor = JdbcQueryExecutor(transactionManager, telemetry)

    should("name the span after the operation and the table of the query it was given") {
        val caller = telemetry.tracerProvider.get("test").spanBuilder("caller").startSpan()
        withContext(caller.asContextElement()) {
            executor.executeSelect(dslContext, dslContext.selectFrom(orders), orders)
        }
        val span = spanExporter.finishedSpanItems.single { it.name != "caller" }
        span.name shouldBe "SELECT payment_order"
        span.attributes[stringKey("db.operation.name")] shouldBe "SELECT"
        span.attributes[stringKey("db.collection.name")] shouldBe "payment_order"
        span.attributes[stringKey("db.system")] shouldBe "postgresql"
        span.kind shouldBe CLIENT
    }
})
