package com.razz.eva.persistence.jdbc.executor

import com.razz.eva.persistence.PersistenceException.ConnectionException
import com.razz.eva.persistence.jdbc.JdbcConnectionElement
import com.razz.eva.persistence.jdbc.JdbcConnectionProvider
import com.razz.eva.persistence.jdbc.JdbcTransactionManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import io.opentelemetry.api.OpenTelemetry.noop
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.Record
import org.jooq.SQLDialect.POSTGRES
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockResult

private val storeTable = object : TableImpl<Record>(DSL.name("store_test")) {
    val ID = createField(DSL.name("id"), SQLDataType.UUID)
    val NAME = createField(DSL.name("name"), SQLDataType.VARCHAR)
}

class JdbcQueryExecutorSpec : BehaviorSpec({

    val dslContext = DSL.using(POSTGRES)
    val select = DSL.using(POSTGRES).selectFrom("SELECT * FROM table")
    val store = DSL.using(POSTGRES).updateQuery(DSL.table("table"))
    val delete = DSL.using(POSTGRES).deleteQuery(DSL.table("table"))

    Given("Jdbc query executor with connection provider") {
        val connectionProvider = mockk<JdbcConnectionProvider>(relaxed = true)
        val jdbcTransactionManager = spyk(JdbcTransactionManager(connectionProvider, connectionProvider))
        val jdbcExecutor = JdbcQueryExecutor(jdbcTransactionManager, noop())

        And("Connection from provider") {
            clearMocks(connectionProvider, answers = false)
            clearMocks(jdbcTransactionManager, answers = false)
            val connection = mockk<Connection>(relaxed = true)
            coEvery { connectionProvider.acquire() } coAnswers { connection }

            When("Principal calls execute select without context") {

                jdbcExecutor.executeSelect(
                    dslContext,
                    select,
                    DSL.table("cool_table"),
                )

                Then("Connection was acquired and released on delegate provider") {
                    coVerify(exactly = 1) {
                        connectionProvider.acquire()
                        connectionProvider.release(connection)
                    }
                }
            }
        }

        And("Another connection from provider") {
            clearMocks(connectionProvider, answers = false)
            clearMocks(jdbcTransactionManager, answers = false)
            val connection = mockk<Connection>(relaxed = true)
            coEvery { connectionProvider.acquire() } coAnswers { connection }

            When("Principal calls execute store without context") {

                val storeRun = suspend {
                    jdbcExecutor.executeStore(
                        dslContext,
                        store,
                        DSL.table("cool_table"),
                    )
                }

                Then("Exception thrown saying there is context missing") {
                    val ex = shouldThrow<IllegalStateException> { storeRun() }
                    ex.message shouldBe "Required existing connection but no existing connection was found"
                }
                And("Connection was not acquired and was not released on delegate provider") {
                    coVerify(exactly = 0) {
                        connectionProvider.acquire()
                        connectionProvider.release(connection)
                    }
                }
            }
        }

        And("Another connection from provider") {
            clearMocks(connectionProvider, answers = false)
            clearMocks(jdbcTransactionManager, answers = false)
            val connection = mockk<Connection>(relaxed = true)
            coEvery { connectionProvider.acquire() } coAnswers { connection }

            When("Principal calls execute delete without context") {

                val storeRun = suspend {
                    jdbcExecutor.executeQuery(
                        dslContext,
                        delete,
                    )
                }

                Then("Exception thrown saying there is context missing") {
                    val ex = shouldThrow<IllegalStateException> { storeRun() }
                    ex.message shouldBe "Required existing connection but no existing connection was found"
                }
                And("Connection was not acquired and was not released on delegate provider") {
                    coVerify(exactly = 0) {
                        connectionProvider.acquire()
                        connectionProvider.release(connection)
                    }
                }
            }
        }

        And("Connection from context") {
            clearMocks(connectionProvider, answers = false)
            clearMocks(jdbcTransactionManager, answers = false)
            val connection = mockk<Connection>(relaxed = true)

            When("Principal calls execute select with context") {

                withContext(Dispatchers.IO + JdbcConnectionElement(connection)) {
                    jdbcExecutor.executeSelect(
                        dslContext,
                        select,
                        DSL.table("cool_table"),
                    )
                }

                Then("Connection was not acquired and was not released on delegate provider") {
                    coVerify(exactly = 0) {
                        connectionProvider.acquire()
                        connectionProvider.release(connection)
                    }
                }
            }
        }

        And("Another connection from context") {
            clearMocks(connectionProvider, answers = false)
            clearMocks(jdbcTransactionManager, answers = false)
            val connection = mockk<Connection>(relaxed = true)

            When("Principal calls execute store with context") {

                withContext(Dispatchers.IO + JdbcConnectionElement(connection)) {
                    jdbcExecutor.executeStore(
                        dslContext,
                        store,
                        DSL.table("cool_table"),
                    )
                }

                Then("Connection was not acquired and was not released on delegate provider") {
                    coVerify(exactly = 0) {
                        connectionProvider.acquire()
                        connectionProvider.release(connection)
                    }
                }
            }
        }

        And("Another connection from context") {
            clearMocks(connectionProvider, answers = false)
            clearMocks(jdbcTransactionManager, answers = false)
            val connection = mockk<Connection>(relaxed = true)

            When("Principal calls execute delete with context") {

                withContext(Dispatchers.IO + JdbcConnectionElement(connection)) {
                    jdbcExecutor.executeQuery(
                        dslContext,
                        delete,
                    )
                }

                Then("Connection was not acquired and was not released on delegate provider") {
                    coVerify(exactly = 0) {
                        connectionProvider.acquire()
                        connectionProvider.release(connection)
                    }
                }
            }
        }
    }

    Given("Jdbc query executor over a jooq mock connection") {
        val connectionProvider = mockk<JdbcConnectionProvider>(relaxed = true)
        val executor = JdbcQueryExecutor(JdbcTransactionManager(connectionProvider, connectionProvider), noop())
        val id = UUID.fromString("b1e9a6a4-3c56-4864-9d9a-4ba33b0480e5")

        fun insertQuery() = dslContext.insertQuery(storeTable).apply {
            addValue(storeTable.ID, id)
            addValue(storeTable.NAME, "razz")
        }

        And("Mock connection returning requested fields") {
            val capturedSql = mutableListOf<String>()
            val connection = MockConnection { ctx ->
                capturedSql += ctx.sql()
                val result = DSL.using(POSTGRES).newResult(storeTable.ID)
                result.add(DSL.using(POSTGRES).newRecord(storeTable.ID).values(id))
                arrayOf(MockResult(1, result))
            }

            When("Principal calls execute store with explicit returning fields") {
                val stored = withContext(Dispatchers.IO + JdbcConnectionElement(connection)) {
                    executor.executeStore(dslContext, insertQuery(), storeTable, listOf(storeTable.ID))
                }

                Then("Returning clause contains only the requested fields") {
                    val returning = capturedSql.single().substringAfter("returning")
                    returning shouldContain "\"id\""
                    returning shouldNotContain "\"name\""
                }
                And("Only the requested fields are populated") {
                    val record = stored.single()
                    record.get(storeTable.ID) shouldBe id
                    record.get(storeTable.NAME) shouldBe null
                }
            }
        }

        And("Mock connection returning requested fields for two rows") {
            val secondId = UUID.fromString("06c04353-c8e4-4ea9-a973-c688a6779b04")
            val connection = MockConnection {
                val result = DSL.using(POSTGRES).newResult(storeTable.ID)
                result.add(DSL.using(POSTGRES).newRecord(storeTable.ID).values(id))
                result.add(DSL.using(POSTGRES).newRecord(storeTable.ID).values(secondId))
                arrayOf(MockResult(2, result))
            }

            When("Principal calls execute store for two rows with explicit returning fields") {
                val insert = dslContext.insertQuery(storeTable).apply {
                    addValue(storeTable.ID, id)
                    addValue(storeTable.NAME, "razz")
                    newRecord()
                    addValue(storeTable.ID, secondId)
                    addValue(storeTable.NAME, "team")
                }
                val stored = withContext(Dispatchers.IO + JdbcConnectionElement(connection)) {
                    executor.executeStore(dslContext, insert, storeTable, listOf(storeTable.ID))
                }

                Then("Each returned record has only the requested fields populated") {
                    stored.map { it.get(storeTable.ID) } shouldBe listOf(id, secondId)
                    stored.map { it.get(storeTable.NAME) } shouldBe listOf(null, null)
                }
            }
        }

        And("Mock connection returning full rows") {
            val capturedSql = mutableListOf<String>()
            val connection = MockConnection { ctx ->
                capturedSql += ctx.sql()
                val result = DSL.using(POSTGRES).newResult(storeTable.ID, storeTable.NAME)
                result.add(DSL.using(POSTGRES).newRecord(storeTable.ID, storeTable.NAME).values(id, "razz"))
                arrayOf(MockResult(1, result))
            }

            When("Principal calls execute store without explicit returning fields") {
                val stored = withContext(Dispatchers.IO + JdbcConnectionElement(connection)) {
                    executor.executeStore(dslContext, insertQuery(), storeTable)
                }

                Then("Returning clause contains all table columns") {
                    val returning = capturedSql.single().substringAfter("returning")
                    returning shouldContain "\"id\""
                    returning shouldContain "\"name\""
                }
                And("All fields are populated") {
                    val record = stored.single()
                    record.get(storeTable.ID) shouldBe id
                    record.get(storeTable.NAME) shouldBe "razz"
                }
            }
        }

        When("Principal calls execute store with empty returning fields") {
            Then("Exception thrown saying returning fields must not be empty") {
                val ex = shouldThrow<IllegalArgumentException> {
                    executor.executeStore(dslContext, insertQuery(), storeTable, listOf())
                }
                ex.message shouldBe "Returning fields must not be empty, use executeQuery for a row count"
            }
        }
    }

    Given("Jdbc query executor extracting connection exceptions") {
        val executor = JdbcQueryExecutor(mockk(relaxed = true))

        When("DataAccessException of connection class is extracted") {
            val connectionLoss = DataAccessException("read failed", SQLException("connection reset", "08006"))
            val extracted = executor.extractConnectionException(connectionLoss)

            Then("ConnectionException with the original cause is returned") {
                extracted.shouldBeTypeOf<ConnectionException>()
                extracted.cause shouldBe connectionLoss
            }
        }

        When("DataAccessException of admin shutdown is extracted") {
            val adminShutdown = DataAccessException("terminating", SQLException("shutting down", "57P01"))
            val extracted = executor.extractConnectionException(adminShutdown)

            Then("ConnectionException with the original cause is returned") {
                extracted.shouldBeTypeOf<ConnectionException>()
                extracted.cause shouldBe adminShutdown
            }
        }

        When("DataAccessException of too many connections is extracted") {
            val tooManyConnections = DataAccessException("refused", SQLException("too many clients", "53300"))

            Then("ConnectionException is returned") {
                executor.extractConnectionException(tooManyConnections).shouldBeTypeOf<ConnectionException>()
            }
        }

        When("DataAccessException of query cancel is extracted") {
            val queryCanceled = DataAccessException("canceled", SQLException("statement timeout", "57014"))

            Then("Nothing is extracted because the connection survives a statement timeout") {
                executor.extractConnectionException(queryCanceled) shouldBe null
            }
        }

        When("DataAccessException of constraint class is extracted") {
            val uniqueViolation = DataAccessException("duplicate", SQLException("duplicate key", "23505"))

            Then("Nothing is extracted") {
                executor.extractConnectionException(uniqueViolation) shouldBe null
            }
        }

        When("Exception which is not a DataAccessException is extracted") {
            Then("Nothing is extracted") {
                executor.extractConnectionException(IllegalStateException("not jooq")) shouldBe null
            }
        }
    }
})
