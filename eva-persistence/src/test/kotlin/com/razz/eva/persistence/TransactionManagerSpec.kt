package com.razz.eva.persistence

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk

class TransactionManagerSpec : BehaviorSpec({
    Given("Dummy transaction manager with empty ctx") {
        val connection = mockk<DummyConnection>()
        val connectionProvider = mockk<DummyConnectionProvider>()
        val transactionManager = WithCtxConnectionTransactionManager(connectionProvider = connectionProvider)

        coEvery { connectionProvider.acquire() } coAnswers { connection }
        coEvery { connectionProvider.release(connection) } returns Unit
        When("Principal calls withConnection") {
            val conn = transactionManager.withConnection { it }

            Then("acquired connection should be returned by delegate provider") {
                coVerify(exactly = 1) { connectionProvider.acquire() }
                conn shouldBe connection
            }

            And("release connection should be called on delegate provider") {
                coVerify(exactly = 1) { connectionProvider.release(connection) }
            }

            And("No more calls to connection provider should be made") {
                confirmVerified(connectionProvider)
            }
        }
    }

    Given("Dummy transaction manager with some ctx connection") {
        val connection = mockk<DummyConnection>()
        val connectionProvider = mockk<DummyConnectionProvider>()
        val ctxConnection = mockk<DummyConnection>()
        val transactionManager = WithCtxConnectionTransactionManager({ ctxConnection }, connectionProvider)

        coEvery { connectionProvider.acquire() } coAnswers { connection }
        When("Principal calls withConnection") {
            val conn = transactionManager.withConnection { it }

            Then("acquired connection should not be returned by delegate provider") {
                coVerify(exactly = 0) { connectionProvider.acquire() }
                conn shouldNotBe connection
                conn shouldBe ctxConnection
            }

            And("release connection should not be called on delegate provider") {
                coVerify(exactly = 0) { connectionProvider.release(connection) }
            }

            And("No more calls to connection provider should be made") {
                confirmVerified(connectionProvider)
            }
        }
    }
})
