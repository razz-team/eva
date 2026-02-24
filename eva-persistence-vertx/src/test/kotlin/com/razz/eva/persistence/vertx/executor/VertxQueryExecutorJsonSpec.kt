package com.razz.eva.persistence.vertx.executor

import com.razz.eva.persistence.vertx.PgPoolConnectionProvider
import com.razz.eva.persistence.vertx.VertxConnectionElement
import com.razz.eva.persistence.vertx.VertxTransactionManager
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.vertx.core.Future.succeededFuture
import io.vertx.core.buffer.Buffer
import io.vertx.pgclient.PgConnection
import io.vertx.sqlclient.PreparedQuery
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
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

    should("cast JSONB field to varchar in SELECT") {
        executor.executeSelect(dslContext, dslContext.selectFrom(jsonTable), jsonTable)

        sqlSlot.captured shouldBe """
            select "json_test"."id", "json_test"."name", cast("json_test"."data" as varchar) as "data"
            from "json_test"
        """.trim().replace(Regex("\\s+"), " ")
    }

    should("not modify SELECT for table without JSON fields") {
        resetMocks()
        executor.executeSelect(dslContext, dslContext.selectFrom(plainTable), plainTable)

        sqlSlot.captured shouldBe
            """select "plain_test"."id", "plain_test"."name" from "plain_test""""
    }

    should("cast JSONB to varchar in RETURNING and bind JSONB param as Buffer") {
        resetMocks()
        val insert = dslContext.insertQuery(jsonTable).apply {
            addValue(jsonTable.ID, UUID.randomUUID())
            addValue(jsonTable.NAME, "test")
            addValue(jsonTable.DATA, JSONB.jsonb("""{"key":"value"}"""))
        }

        withContext(Dispatchers.IO + VertxConnectionElement(connection)) {
            executor.executeStore(dslContext, insert, jsonTable)
        }

        sqlSlot.captured shouldBe """
            insert into "json_test" ("id", "name", "data")
            values (cast(:1 as uuid), :2, cast(:3 as jsonb))
            returning "json_test"."id", "json_test"."name", cast("json_test"."data" as varchar) as "data"
        """.trim().replace(Regex("\\s+"), " ")

        val values = (0 until paramsSlot.captured.size()).map { paramsSlot.captured.getValue(it) }
        val buffer = values.filterIsInstance<Buffer>().single()
        buffer.toString() shouldBe """{"key":"value"}"""
    }

    should("not modify RETURNING for table without JSON fields") {
        resetMocks()
        val insert = dslContext.insertQuery(plainTable).apply {
            addValue(plainTable.ID, UUID.randomUUID())
            addValue(plainTable.NAME, "test")
        }

        withContext(Dispatchers.IO + VertxConnectionElement(connection)) {
            executor.executeStore(dslContext, insert, plainTable)
        }

        sqlSlot.captured shouldBe """
            insert into "plain_test" ("id", "name")
            values (cast(:1 as uuid), :2)
            returning "plain_test"."id", "plain_test"."name"
        """.trim().replace(Regex("\\s+"), " ")
    }
})
