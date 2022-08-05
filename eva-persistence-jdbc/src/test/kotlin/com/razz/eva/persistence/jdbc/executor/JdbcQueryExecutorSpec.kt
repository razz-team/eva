package com.razz.eva.persistence.jdbc.executor

import com.razz.eva.persistence.jdbc.JdbcConnectionElement
import com.razz.eva.persistence.jdbc.JdbcConnectionProvider
import com.razz.eva.persistence.jdbc.JdbcTransactionManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import java.sql.Connection

class JdbcQueryExecutorSpec : BehaviorSpec({

    val dslContext = DSL.using(POSTGRES)
    val select = DSL.using(POSTGRES).selectFrom("SELECT * FROM table")
    val store = DSL.using(POSTGRES).updateQuery(DSL.table("table"))
    val delete = DSL.using(POSTGRES).deleteQuery(DSL.table("table"))

    Given("Jdbc query executor with connection provider") {
        val connectionProvider = mockk<JdbcConnectionProvider>(relaxed = true)
        val jdbcTransactionManager = spyk(JdbcTransactionManager(connectionProvider, connectionProvider))
        val jdbcExecutor = JdbcQueryExecutor(jdbcTransactionManager)

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
                    ex.message shouldBe "Required existing connection"
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
                    jdbcExecutor.executeDelete(
                        dslContext,
                        delete,
                        DSL.table("cool_table"),
                    )
                }

                Then("Exception thrown saying there is context missing") {
                    val ex = shouldThrow<IllegalStateException> { storeRun() }
                    ex.message shouldBe "Required existing connection"
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
                    jdbcExecutor.executeDelete(
                        dslContext,
                        delete,
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
    }
})
