package com.razz.eva.persistence.vertx.executor

import com.razz.eva.persistence.vertx.PgPoolConnectionProvider
import com.razz.eva.persistence.vertx.VertxConnectionElement
import com.razz.eva.persistence.vertx.VertxTransactionManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.vertx.core.Future.succeededFuture
import io.vertx.pgclient.PgConnection
import io.vertx.sqlclient.PreparedQuery
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.impl.ListTuple
import java.util.UUID
import java.util.function.Function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.Record
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl

private val storeTable = object : TableImpl<Record>(DSL.name("store_test")) {
    val ID = createField(DSL.name("id"), SQLDataType.UUID)
    val NAME = createField(DSL.name("name"), SQLDataType.VARCHAR)
}

class VertxQueryExecutorReturningSpec : ShouldSpec({

    val dslContext = DSL.using(POSTGRES)
    val connectionProvider = mockk<PgPoolConnectionProvider>(relaxed = true)
    val transactionManager = spyk(VertxTransactionManager(connectionProvider, connectionProvider))
    val executor = VertxQueryExecutor(transactionManager)

    val sqlSlot = slot<String>()
    val mappingSlot = slot<Function<Row, Record>>()

    val preparedQueryMock = mockk<PreparedQuery<RowSet<Row>>> {
        every { mapping(capture(mappingSlot)) } answers {
            mockk {
                every { execute(any<ListTuple>()) } returns succeededFuture(
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

    fun resetMocks() {
        clearMocks(connection, answers = false)
        every { connection.preparedQuery(capture(sqlSlot)) } answers { preparedQueryMock }
    }

    fun insertQuery() = dslContext.insertQuery(storeTable).apply {
        addValue(storeTable.ID, UUID.randomUUID())
        addValue(storeTable.NAME, "razz")
    }

    should("render only caller-provided returning fields") {
        resetMocks()
        withContext(Dispatchers.IO + VertxConnectionElement(connection)) {
            executor.executeStore(dslContext, insertQuery(), storeTable, listOf(storeTable.ID))
        }

        sqlSlot.captured shouldContain "returning \"store_test\".\"id\""
        sqlSlot.captured.substringAfter("returning") shouldNotContain "\"name\""
    }

    should("populate only caller-provided returning fields") {
        resetMocks()
        withContext(Dispatchers.IO + VertxConnectionElement(connection)) {
            executor.executeStore(dslContext, insertQuery(), storeTable, listOf(storeTable.ID))
        }

        val id = UUID.randomUUID()
        val row = mockk<Row> { every { get(UUID::class.java, 0) } returns id }
        val record = mappingSlot.captured.apply(row)
        record.get(storeTable.ID) shouldBe id
        record.get(storeTable.NAME) shouldBe null
    }

    should("render all table columns when no returning fields provided") {
        resetMocks()
        withContext(Dispatchers.IO + VertxConnectionElement(connection)) {
            executor.executeStore(dslContext, insertQuery(), storeTable)
        }

        sqlSlot.captured shouldContain "returning \"store_test\".\"id\", \"store_test\".\"name\""
    }

    should("reject empty returning fields") {
        val ex = shouldThrow<IllegalArgumentException> {
            executor.executeStore(dslContext, insertQuery(), storeTable, listOf())
        }
        ex.message shouldBe "Returning fields must not be empty, use executeQuery for a row count"
    }
})
