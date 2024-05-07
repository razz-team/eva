package com.razz.eva.persistence.jdbc

import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import com.razz.eva.persistence.PrimaryConnectionRequiredFlag
import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.sql.Connection

class JdbcTransactionManagerSpec : BehaviorSpec({

    val primaryPool = mockk<HikariDataSource>()
    val replicaPool = mockk<HikariDataSource>()

    Given("Jdbc transaction manager with pooled connection provider") {
        val primaryProvider = HikariPoolConnectionProvider(primaryPool)
        val replicaProvider = HikariPoolConnectionProvider(replicaPool)
        val jdbcTransactionManager = JdbcTransactionManager(primaryProvider, replicaProvider)

        When("Principal asks pipelining support") {
            val supportsPipelining = jdbcTransactionManager.supportsPipelining()

            Then("Answer should be negative") {
                supportsPipelining shouldBe false
            }
        }

        When("Principal calling action asking for existing transaction in scope with no transaction") {
            clearMocks(primaryPool, answers = false)
            clearMocks(replicaPool, answers = false)

            val call = suspend {
                jdbcTransactionManager.inTransaction(REQUIRE_EXISTING) { _ ->
                    TODO("NEVER HAPPENS")
                }
            }

            Then("Exception thrown") {
                val ex = shouldThrow<IllegalStateException> { call() }
                ex.message shouldBe "Required existing connection"
            }

            And("Pools were not called") {
                confirmVerified(primaryPool)
                confirmVerified(replicaPool)
            }
        }

        When("Principal calling action asking for new transaction in scope with transaction") {
            clearMocks(primaryPool, answers = false)
            clearMocks(replicaPool, answers = false)

            val connection = mockk<Connection>(relaxed = true)
            every { connection.autoCommit } returns true
            every { primaryPool.connection } returns connection

            val call = suspend {
                jdbcTransactionManager.inTransaction(REQUIRE_NEW) { _ ->
                    jdbcTransactionManager.inTransaction(REQUIRE_NEW) { _ ->
                        TODO("NEVER HAPPENS")
                    }
                }
            }

            Then("Connection rollback transaction once and in right order") {
                val ex = shouldThrow<IllegalStateException> { call() }
                verifyOrder {
                    primaryPool.connection
                    connection.autoCommit
                    connection.autoCommit = false
                    connection.isClosed
                    connection.rollback()
                    connection.autoCommit = true
                    connection.close()
                }
                ex.message shouldBe "Required new connection"
            }

            And("Pool connections was acquired and returned, connection was rolled back") {
                verify(exactly = 1) {
                    primaryPool.connection
                }
                verify(exactly = 1) {
                    connection.autoCommit
                }
                verify(exactly = 1) {
                    connection.autoCommit = false
                }
                verify(exactly = 1) {
                    connection.isClosed
                }
                verify(exactly = 1) {
                    connection.rollback()
                }
                verify(exactly = 1) {
                    connection.autoCommit = true
                }
                verify(exactly = 1) {
                    connection.close()
                }
                confirmVerified(primaryPool)
                confirmVerified(replicaPool)
                confirmVerified(connection)
            }
        }

        And("Transactional action") {
            var count = 0
            val action = suspend {
                jdbcTransactionManager.withConnection {
                    count++
                }
            }

            When("Principal calls action three times asking for new transaction") {
                clearMocks(primaryPool, answers = false)
                clearMocks(replicaPool, answers = false)

                val connection = mockk<Connection>(relaxed = true)
                every { connection.autoCommit } returns true
                every { primaryPool.connection } returns connection

                jdbcTransactionManager.inTransaction(REQUIRE_NEW) { _ ->
                    action(); action(); action()
                }

                Then("Action was called three times") {
                    count shouldBe 3
                }

                And("Connection committed transaction once and in right order") {
                    verifyOrder {
                        primaryPool.connection
                        connection.autoCommit
                        connection.autoCommit = false
                        connection.commit()
                        connection.autoCommit = true
                        connection.close()
                    }
                }

                And("Only one pool connection was acquired and returned, connection committed") {
                    verify(exactly = 1) {
                        primaryPool.connection
                    }
                    verify(exactly = 1) {
                        connection.autoCommit
                    }
                    verify(exactly = 1) {
                        connection.autoCommit = false
                    }
                    verify(exactly = 1) {
                        connection.commit()
                    }
                    verify(exactly = 1) {
                        connection.autoCommit = true
                    }
                    verify(exactly = 1) {
                        connection.close()
                    }
                    confirmVerified(primaryPool)
                    confirmVerified(replicaPool)
                    confirmVerified(connection)
                }
            }

            When("Principal calls action three times asking for existing transaction") {
                clearMocks(primaryPool, answers = false)
                clearMocks(replicaPool, answers = false)
                count = 0

                val connection = mockk<Connection>(relaxed = true)
                every { connection.autoCommit } returns true
                every { primaryPool.connection } returns connection

                withContext(Dispatchers.IO + JdbcConnectionElement(connection)) {
                    jdbcTransactionManager.inTransaction(REQUIRE_EXISTING) { _ ->
                        action(); action(); action()
                    }
                }

                Then("Action was called three times") {
                    count shouldBe 3
                }

                And("Connection committed transaction once and in right order") {
                    verifyOrder {
                        connection.autoCommit
                    }
                }

                And("No pool connections were acquired and returned") {
                    verify(exactly = 0) {
                        primaryPool.connection
                    }
                    // JdbcConnectionElement ctor
                    verify(exactly = 1) {
                        connection.autoCommit
                    }
                    verify(exactly = 0) {
                        connection.autoCommit = false
                    }
                    verify(exactly = 0) {
                        connection.close()
                    }
                    confirmVerified(primaryPool)
                    confirmVerified(replicaPool)
                    confirmVerified(connection)
                }
            }

            When("Principal calls action with primary flag in context") {
                clearMocks(primaryPool, answers = false)
                clearMocks(replicaPool, answers = false)
                count = 0

                val connection = mockk<Connection>(relaxed = true)
                every { primaryPool.connection } returns connection

                withContext(PrimaryConnectionRequiredFlag) {
                    action()
                }

                Then("Action was called one time") {
                    count shouldBe 1
                }

                And("One pool connections was acquired and returned") {
                    verify(exactly = 1) {
                        primaryPool.connection
                    }
                    verify(exactly = 1) {
                        connection.close()
                    }
                    confirmVerified(primaryPool)
                    confirmVerified(replicaPool)
                    confirmVerified(connection)
                }
            }

            When("Principal calls action with no primary flag in context") {
                clearMocks(primaryPool, answers = false)
                clearMocks(replicaPool, answers = false)
                count = 0

                val connection = mockk<Connection>(relaxed = true)
                every { replicaPool.connection } returns connection

                action()

                Then("Action was called one time") {
                    count shouldBe 1
                }

                And("One pool connections was acquired and returned") {
                    verify(exactly = 1) {
                        replicaPool.connection
                    }
                    verify(exactly = 1) {
                        connection.close()
                    }
                    confirmVerified(primaryPool)
                    confirmVerified(replicaPool)
                    confirmVerified(connection)
                }
            }
        }

        And("Long running action") {
            var count = 0
            val actionStarted = Channel<Boolean>(1)
            val action = suspend {
                actionStarted.send(true)
                delay(100_000)
                count++
            }

            And("Principal runs action with connection in cancelable coroutine") {
                clearMocks(primaryPool, answers = false)
                clearMocks(replicaPool, answers = false)

                val connection = mockk<Connection>(relaxed = true)
                every { replicaPool.connection } returns connection
                val coroutine = async {
                    jdbcTransactionManager.withConnection { _ -> action() }
                }

                When("Principal cancels coroutine") {
                    actionStarted.receive()
                    coroutine.cancel()

                    Then("Count logic was not called") {
                        count shouldBe 0
                    }

                    And("Connection was acquired and released") {
                        verifyOrder {
                            replicaPool.connection
                            connection.close()
                        }
                    }

                    And("Only one pool connection was acquired and returned") {
                        verify(exactly = 1) {
                            replicaPool.connection
                        }
                        verify(exactly = 1) {
                            connection.close()
                        }
                        confirmVerified(primaryPool)
                        confirmVerified(replicaPool)
                        confirmVerified(connection)
                    }
                }
            }
        }

        And("Transactional action throwing error") {
            val bad = suspend {
                jdbcTransactionManager.withConnection {
                    throw IllegalStateException("Something bad")
                }
            }

            When("Principal try call throwing error action asking for new transaction") {
                clearMocks(primaryPool, answers = false)
                clearMocks(replicaPool, answers = false)

                val connection = mockk<Connection>(relaxed = true)
                every { connection.autoCommit } returns true
                every { primaryPool.connection } returns connection

                val call = suspend {
                    jdbcTransactionManager.inTransaction(REQUIRE_NEW) { _ ->
                        bad()
                    }
                }

                Then("Connection rollback transaction once and in right order") {
                    val ex = shouldThrow<IllegalStateException> { call() }
                    verifyOrder {
                        primaryPool.connection
                        connection.autoCommit
                        connection.autoCommit = false
                        connection.isClosed
                        connection.rollback()
                        connection.autoCommit = true
                        connection.close()
                    }
                    ex.message shouldBe "Something bad"
                }

                And("Only one pool connection was acquired and returned") {
                    verify(exactly = 1) {
                        primaryPool.connection
                    }
                    verify(exactly = 1) {
                        connection.autoCommit
                    }
                    verify(exactly = 1) {
                        connection.autoCommit = false
                    }
                    verify(exactly = 1) {
                        connection.isClosed
                    }
                    verify(exactly = 1) {
                        connection.rollback()
                    }
                    verify(exactly = 1) {
                        connection.autoCommit = true
                    }
                    verify(exactly = 1) {
                        connection.close()
                    }
                    confirmVerified(primaryPool)
                    confirmVerified(replicaPool)
                    confirmVerified(connection)
                }
            }

            When("Principal try call throwing error action asking for existing transaction") {
                clearMocks(primaryPool, answers = false)
                clearMocks(replicaPool, answers = false)

                val connection = mockk<Connection>(relaxed = true)
                every { connection.autoCommit } returns true
                every { primaryPool.connection } returns connection

                val call = suspend {
                    withContext(Dispatchers.IO + JdbcConnectionElement(connection)) {
                        jdbcTransactionManager.inTransaction(REQUIRE_EXISTING) { _ ->
                            bad()
                        }
                    }
                }

                Then("Exception is thrown") {
                    val ex = shouldThrow<IllegalStateException> { call() }
                    ex.message shouldBe "Something bad"
                }

                And("No pool connections were acquired and returned") {
                    verify(exactly = 0) {
                        primaryPool.connection
                    }
                    // JdbcConnectionElement ctor
                    verify(exactly = 1) {
                        connection.autoCommit
                    }
                    verify(exactly = 0) {
                        connection.autoCommit = false
                    }
                    verify(exactly = 0) {
                        connection.close()
                    }
                    confirmVerified(primaryPool)
                    confirmVerified(replicaPool)
                    confirmVerified(connection)
                }
            }

            When("Principal try call throwing error action asking for existing transaction in scope with transaction") {
                clearMocks(primaryPool, answers = false)
                clearMocks(replicaPool, answers = false)

                val connection = mockk<Connection>(relaxed = true)
                every { connection.autoCommit } returns true
                every { primaryPool.connection } returns connection

                val call = suspend {
                    jdbcTransactionManager.inTransaction(REQUIRE_NEW) { _ ->
                        jdbcTransactionManager.inTransaction(REQUIRE_EXISTING) { _ ->
                            bad()
                        }
                    }
                }

                Then("Connection rollback transaction once and in right order") {
                    val ex = shouldThrow<IllegalStateException> { call() }
                    verifyOrder {
                        primaryPool.connection
                        connection.autoCommit
                        connection.autoCommit = false
                        connection.isClosed
                        connection.rollback()
                        connection.autoCommit = true
                        connection.close()
                    }
                    ex.message shouldBe "Something bad"
                }

                And("Pool connections was acquired and returned, connection was rolled back") {
                    verify(exactly = 1) {
                        primaryPool.connection
                    }
                    verify(exactly = 1) {
                        connection.autoCommit
                    }
                    verify(exactly = 1) {
                        connection.autoCommit = false
                    }
                    verify(exactly = 1) {
                        connection.isClosed
                    }
                    verify(exactly = 1) {
                        connection.rollback()
                    }
                    verify(exactly = 1) {
                        connection.autoCommit = true
                    }
                    verify(exactly = 1) {
                        connection.close()
                    }
                    confirmVerified(primaryPool)
                    confirmVerified(replicaPool)
                    confirmVerified(connection)
                }
            }
        }
    }
})
