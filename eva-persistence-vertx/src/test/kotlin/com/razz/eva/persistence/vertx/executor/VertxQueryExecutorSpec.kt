package com.razz.eva.persistence.vertx.executor

import com.razz.eva.persistence.vertx.PgPoolConnectionProvider
import com.razz.eva.persistence.vertx.VertxConnectionElement
import com.razz.eva.persistence.vertx.VertxTransactionManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.vertx.core.Future.succeededFuture
import io.vertx.pgclient.PgConnection
import io.vertx.sqlclient.PreparedQuery
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.impl.ListTuple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import java.util.function.Function

class VertxQueryExecutorSpec : BehaviorSpec({

    val dslContext = DSL.using(POSTGRES)
    val select = DSL.using(POSTGRES).selectFrom("SELECT * FROM table")
    val store = DSL.using(POSTGRES).updateQuery(DSL.table("table"))
    val delete = DSL.using(POSTGRES).deleteQuery(DSL.table("table"))

    Given("Vertx query executor with connection provider") {
        val connectionProvider = mockk<PgPoolConnectionProvider>(relaxed = true)
        val vertxTransactionManager = spyk(VertxTransactionManager(connectionProvider, connectionProvider))
        val vertxExecutor = VertxQueryExecutor(vertxTransactionManager)
        val preparedQueryMock = mockk<PreparedQuery<RowSet<Row>>> {
            every { execute(any<ListTuple>()) } returns succeededFuture(
                mockk {
                    every { rowCount() } returns 0
                    every { iterator() } answers {
                        mockk {
                            every { hasNext() } returns false
                        }
                    }
                    every { size() } returns 0
                },
            )
            every { mapping(any<Function<Row, Any>>()) } answers {
                mockk {
                    every { execute(any<ListTuple>()) } returns succeededFuture(
                        mockk {
                            every { iterator() } answers {
                                mockk {
                                    every { hasNext() } returns false
                                }
                            }
                            every { size() } returns 0
                        },
                    )
                }
            }
        }
        val connection = mockk<PgConnection>(relaxed = true) {
            every { preparedQuery(dslContext.renderNamedParams(select)) } answers { preparedQueryMock }
            every { preparedQuery(dslContext.renderNamedParams(store)) } answers { preparedQueryMock }
            every { preparedQuery(dslContext.renderNamedParams(delete)) } answers { preparedQueryMock }
        }

        And("Connection from provider") {
            clearMocks(connectionProvider, answers = false)
            clearMocks(vertxTransactionManager, answers = false)
            coEvery { connectionProvider.acquire() } coAnswers { connection }

            When("Principal calls execute select without context") {

                vertxExecutor.executeSelect(
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
            clearMocks(connection, answers = false)
            clearMocks(vertxTransactionManager, answers = false)
            coEvery { connectionProvider.acquire() } coAnswers { connection }

            When("Principal calls execute store without context") {

                val storeRun = suspend {
                    vertxExecutor.executeStore(
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
            clearMocks(connection, answers = false)
            clearMocks(vertxTransactionManager, answers = false)
            coEvery { connectionProvider.acquire() } coAnswers { connection }

            When("Principal calls execute store without context") {

                val storeRun = suspend {
                    vertxExecutor.executeQuery(
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
            clearMocks(connection, answers = false)
            clearMocks(vertxTransactionManager, answers = false)

            When("Principal calls execute select with context") {

                withContext(Dispatchers.IO + VertxConnectionElement(connection)) {
                    vertxExecutor.executeSelect(
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
            clearMocks(connection, answers = false)
            clearMocks(vertxTransactionManager, answers = false)

            When("Principal calls execute store with context") {

                withContext(Dispatchers.IO + VertxConnectionElement(connection)) {
                    vertxExecutor.executeStore(
                        dslContext,
                        store,
                        DSL.table("cool_table"),
                    )
                }

                Then("Connection was not acquired and was not released on delegate provider") {
                    coVerify(exactly = 0) {
                        connectionProvider.release(connection)
                        connectionProvider.acquire()
                    }
                }
            }
        }

        And("Another connection from context") {
            clearMocks(connectionProvider, answers = false)
            clearMocks(connection, answers = false)
            clearMocks(vertxTransactionManager, answers = false)

            When("Principal calls execute delete with context") {

                withContext(Dispatchers.IO + VertxConnectionElement(connection)) {
                    vertxExecutor.executeQuery(
                        dslContext,
                        delete,
                    )
                }

                Then("Connection was not acquired and was not released on delegate provider") {
                    coVerify(exactly = 0) {
                        connectionProvider.release(connection)
                        connectionProvider.acquire()
                    }
                }
            }
        }
    }
})
