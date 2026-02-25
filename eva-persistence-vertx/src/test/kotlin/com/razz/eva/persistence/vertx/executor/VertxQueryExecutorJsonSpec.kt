package com.razz.eva.persistence.vertx.executor

import com.razz.eva.persistence.vertx.PgPoolConnectionProvider
import com.razz.eva.persistence.vertx.VertxConnectionElement
import com.razz.eva.persistence.vertx.VertxTransactionManager
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.vertx.core.Future.succeededFuture
import io.vertx.core.json.JsonObject
import io.vertx.pgclient.PgConnection
import io.vertx.sqlclient.PreparedQuery
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import io.vertx.sqlclient.impl.ListTuple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl
import java.util.UUID
import java.util.function.Function

private val jsonTable = object : TableImpl<Record>(DSL.name("json_test")) {
    val ID = createField(DSL.name("id"), SQLDataType.UUID)!!
    val NAME = createField(DSL.name("name"), SQLDataType.VARCHAR)!!
    val DATA = createField(DSL.name("data"), SQLDataType.JSONB)!!
}

private val plainTable = object : TableImpl<Record>(DSL.name("plain_test")) {
    val ID = createField(DSL.name("id"), SQLDataType.UUID)!!
    val NAME = createField(DSL.name("name"), SQLDataType.VARCHAR)!!
}

class VertxQueryExecutorJsonSpec : ShouldSpec({

    val dslContext = DSL.using(POSTGRES)
    val connectionProvider = mockk<PgPoolConnectionProvider>(relaxed = true)
    val transactionManager = spyk(VertxTransactionManager(connectionProvider, connectionProvider))
    val executor = VertxQueryExecutor(transactionManager)

    val sqlSlot = slot<String>()
    val paramsSlot = slot<ListTuple>()

    val preparedQueryMock = mockk<PreparedQuery<RowSet<Row>>> {
        every { mapping(any<Function<Row, Any>>()) } answers {
            mockk {
                every { execute(capture(paramsSlot)) } returns succeededFuture(
                    mockk {
                        every { iterator() } answers {
                            mockk { every { hasNext() } returns false }
                        }
                        every { size() } returns 0
                    },
                )
            }
        }
    }

    val connection = mockk<PgConnection>(relaxed = true) {
        every { preparedQuery(capture(sqlSlot)) } answers { preparedQueryMock }
    }
    coEvery { connectionProvider.acquire() } coAnswers { connection }

    fun resetMocks() {
        clearMocks(connection, answers = false)
        every { connection.preparedQuery(capture(sqlSlot)) } answers { preparedQueryMock }
    }

    should("pass JSONB param as decoded JsonObject") {
        resetMocks()
        val insert = dslContext.insertQuery(jsonTable).apply {
            addValue(jsonTable.ID, UUID.randomUUID())
            addValue(jsonTable.NAME, "test")
            addValue(jsonTable.DATA, JSONB.jsonb("""{"key":"value"}"""))
        }

        withContext(Dispatchers.IO + VertxConnectionElement(connection)) {
            executor.executeStore(dslContext, insert, jsonTable)
        }

        val values = (0 until paramsSlot.captured.size()).map { paramsSlot.captured.getValue(it) }
        val jsonValue = values[2]
        jsonValue.shouldBeInstanceOf<JsonObject>()
        jsonValue.getString("key") shouldBe "value"
    }

    should("use JSON_NULL for JSONB null literal") {
        resetMocks()
        val insert = dslContext.insertQuery(jsonTable).apply {
            addValue(jsonTable.ID, UUID.randomUUID())
            addValue(jsonTable.NAME, "test")
            addValue(jsonTable.DATA, JSONB.jsonb("null"))
        }

        withContext(Dispatchers.IO + VertxConnectionElement(connection)) {
            executor.executeStore(dslContext, insert, jsonTable)
        }

        val values = (0 until paramsSlot.captured.size()).map { paramsSlot.captured.getValue(it) }
        values[2] shouldBe Tuple.JSON_NULL
    }

    should("not rewrite SELECT for table with JSON fields") {
        executor.executeSelect(dslContext, dslContext.selectFrom(jsonTable), jsonTable)

        sqlSlot.captured shouldBe
            """select "json_test"."id", "json_test"."name", "json_test"."data" from "json_test""""
    }

    should("not rewrite SELECT for table without JSON fields") {
        resetMocks()
        executor.executeSelect(dslContext, dslContext.selectFrom(plainTable), plainTable)

        sqlSlot.captured shouldBe
            """select "plain_test"."id", "plain_test"."name" from "plain_test""""
    }
})
