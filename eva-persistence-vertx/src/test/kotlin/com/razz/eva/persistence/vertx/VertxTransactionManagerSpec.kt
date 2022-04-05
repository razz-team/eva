package com.razz.eva.persistence.vertx

import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import com.razz.eva.persistence.PrimaryConnectionRequiredFlag
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.vertx.core.Future.succeededFuture
import io.vertx.pgclient.PgConnection
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Transaction
import kotlinx.coroutines.withContext

class VertxTransactionManagerSpec : BehaviorSpec({

    val primaryPool = mockk<PgPool>()
    val replicaPool = mockk<PgPool>()

    Given("Vertx transaction manager with pooled connection provider") {
        val primaryProvider = PgPoolConnectionProvider(primaryPool)
        val replicaProvider = PgPoolConnectionProvider(replicaPool)
        val vetxTransactionManager = VertxTransactionManager(primaryProvider, replicaProvider)

        When("Principal asks pipelining support") {
            val supportsPipelining = vetxTransactionManager.supportsPipelining()

            Then("Answer should be positive") {
                supportsPipelining shouldBe true
            }
        }

        When("Principal calling action asking for existing transaction in scope with no transaction") {
            clearMocks(primaryPool, answers = false)
            clearMocks(replicaPool, answers = false)

            val call = suspend {
                vetxTransactionManager.inTransaction(REQUIRE_EXISTING) { _ ->
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

            val connection = mockk<PgConnection>(relaxed = true)
            val txn = mockk<Transaction>(relaxed = true)
            every { connection.begin() } returns succeededFuture(txn)
            every { connection.close() } returns succeededFuture()
            every { txn.rollback() } returns succeededFuture()
            every { primaryPool.connection } returns succeededFuture(connection)

            val call = suspend {
                vetxTransactionManager.inTransaction(REQUIRE_NEW) { _ ->
                    vetxTransactionManager.inTransaction(REQUIRE_NEW) { _ ->
                        TODO("NEVER HAPPENS")
                    }
                }
            }

            Then("Connection rollback transaction once and in right order") {
                val ex = shouldThrow<IllegalStateException> { call() }
                verifyOrder {
                    primaryPool.connection
                    connection.begin()
                    txn.rollback()
                    connection.close()
                }
                ex.message shouldBe "Required new connection"
            }

            And("Pool connections was acquired and returned, txn was rolled back") {
                verify(exactly = 1) {
                    primaryPool.connection
                }
                verify(exactly = 1) {
                    connection.begin()
                }
                verify(exactly = 1) {
                    connection.close()
                }
                verify(exactly = 1) {
                    txn.rollback()
                }
                confirmVerified(primaryPool)
                confirmVerified(replicaPool)
                confirmVerified(connection)
                confirmVerified(txn)
            }
        }

        And("Transactional action") {
            var count = 0
            val action = suspend {
                vetxTransactionManager.withConnection {
                    count++
                }
            }

            When("Principal calls action three times asking for new transaction") {
                clearMocks(primaryPool, answers = false)
                clearMocks(replicaPool, answers = false)

                val connection = mockk<PgConnection>(relaxed = true)
                val txn = mockk<Transaction>(relaxed = true)
                every { connection.begin() } returns succeededFuture(txn)
                every { connection.close() } returns succeededFuture()
                every { txn.commit() } returns succeededFuture()
                every { primaryPool.connection } returns succeededFuture(connection)

                vetxTransactionManager.inTransaction(REQUIRE_NEW) { _ ->
                    action(); action(); action()
                }

                Then("Action was called three times") {
                    count shouldBe 3
                }

                And("Connection committed transaction once and in right order") {
                    verifyOrder {
                        primaryPool.connection
                        connection.begin()
                        txn.commit()
                        connection.close()
                    }
                }

                And("Only one pool connection was acquired and returned, txn committed") {
                    verify(exactly = 1) {
                        primaryPool.connection
                    }
                    verify(exactly = 1) {
                        connection.begin()
                    }
                    verify(exactly = 1) {
                        txn.commit()
                    }
                    verify(exactly = 1) {
                        connection.close()
                    }
                    confirmVerified(primaryPool)
                    confirmVerified(replicaPool)
                    confirmVerified(connection)
                    confirmVerified(txn)
                }
            }

            When("Principal calls action three times asking for existing transaction") {
                clearMocks(primaryPool, answers = false)
                clearMocks(replicaPool, answers = false)
                count = 0

                val connection = mockk<PgConnection>(relaxed = true)
                every { primaryPool.connection } returns succeededFuture(connection)

                withContext(VertxConnectionElement(connection)) {
                    vetxTransactionManager.inTransaction(REQUIRE_EXISTING) { _ ->
                        action(); action(); action()
                    }
                }

                Then("Action was called three times") {
                    count shouldBe 3
                }

                And("No pool connections were acquired and returned") {
                    verify(exactly = 0) {
                        primaryPool.connection
                    }
                    verify(exactly = 0) {
                        connection.begin()
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

                val connection = mockk<PgConnection>(relaxed = true)
                every { connection.close() } returns succeededFuture()
                every { primaryPool.connection } returns succeededFuture(connection)

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

                val connection = mockk<PgConnection>(relaxed = true)
                every { connection.close() } returns succeededFuture()
                every { replicaPool.connection } returns succeededFuture(connection)

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

        And("Transactional action throwing error") {
            val bad = suspend {
                vetxTransactionManager.withConnection {
                    throw IllegalStateException("Something bad")
                }
            }

            When("Principal try call throwing error action asking for new transaction") {
                clearMocks(primaryPool, answers = false)
                clearMocks(replicaPool, answers = false)

                val connection = mockk<PgConnection>(relaxed = true)
                val txn = mockk<Transaction>(relaxed = true)
                every { connection.begin() } returns succeededFuture(txn)
                every { connection.close() } returns succeededFuture()
                every { txn.rollback() } returns succeededFuture()
                every { primaryPool.connection } returns succeededFuture(connection)

                val call = suspend {
                    vetxTransactionManager.inTransaction(REQUIRE_NEW) { _ ->
                        bad()
                    }
                }

                Then("Connection rollback transaction once and in right order") {
                    val ex = shouldThrow<IllegalStateException> { call() }
                    verifyOrder {
                        primaryPool.connection
                        connection.begin()
                        txn.rollback()
                        connection.close()
                    }
                    ex.message shouldBe "Something bad"
                }

                And("Only one pool connection was acquired and returned") {
                    verify(exactly = 1) {
                        primaryPool.connection
                    }
                    verify(exactly = 1) {
                        connection.begin()
                    }
                    verify(exactly = 1) {
                        txn.rollback()
                    }
                    verify(exactly = 1) {
                        connection.close()
                    }
                    confirmVerified(primaryPool)
                    confirmVerified(replicaPool)
                    confirmVerified(connection)
                    confirmVerified(txn)
                }
            }

            When("Principal try call throwing error action asking for existing transaction") {
                clearMocks(primaryPool, answers = false)
                clearMocks(replicaPool, answers = false)

                val connection = mockk<PgConnection>(relaxed = true)
                every { primaryPool.connection } returns succeededFuture(connection)

                val call = suspend {
                    withContext(VertxConnectionElement(connection)) {
                        vetxTransactionManager.inTransaction(REQUIRE_EXISTING) { _ ->
                            bad()
                        }
                    }
                }

                Then("Exception was thrown") {
                    val ex = shouldThrow<IllegalStateException> { call() }
                    ex.message shouldBe "Something bad"
                }

                And("No pool connections were acquired and returned") {
                    verify(exactly = 0) {
                        primaryPool.connection
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

                val connection = mockk<PgConnection>(relaxed = true)
                val txn = mockk<Transaction>(relaxed = true)
                every { connection.begin() } returns succeededFuture(txn)
                every { connection.close() } returns succeededFuture()
                every { txn.rollback() } returns succeededFuture()
                every { primaryPool.connection } returns succeededFuture(connection)

                val call = suspend {
                    vetxTransactionManager.inTransaction(REQUIRE_NEW) { _ ->
                        vetxTransactionManager.inTransaction(REQUIRE_EXISTING) { _ ->
                            bad()
                        }
                    }
                }

                Then("Connection rollback transaction once and in right order") {
                    val ex = shouldThrow<IllegalStateException> { call() }
                    verifyOrder {
                        primaryPool.connection
                        connection.begin()
                        txn.rollback()
                        connection.close()
                    }
                    ex.message shouldBe "Something bad"
                }

                And("Pool connections was acquired and returned, txn was rolled back") {
                    verify(exactly = 1) {
                        primaryPool.connection
                    }
                    verify(exactly = 1) {
                        connection.begin()
                    }
                    verify(exactly = 1) {
                        connection.close()
                    }
                    verify(exactly = 1) {
                        txn.rollback()
                    }
                    confirmVerified(primaryPool)
                    confirmVerified(replicaPool)
                    confirmVerified(connection)
                    confirmVerified(txn)
                }
            }
        }
    }
})
