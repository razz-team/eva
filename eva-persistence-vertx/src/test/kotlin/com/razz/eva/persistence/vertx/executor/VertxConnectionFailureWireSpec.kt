package com.razz.eva.persistence.vertx.executor

import com.razz.eva.persistence.FakePostgres
import com.razz.eva.persistence.PersistenceException.ConnectionException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.mockk
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgConnection

class VertxConnectionFailureWireSpec : BehaviorSpec({

    val executor = VertxQueryExecutor(mockk(relaxed = true))

    suspend fun failingStatement(fakePostgres: FakePostgres): Exception {
        val vertx = Vertx.vertx()
        return try {
            val options = PgConnectOptions().apply {
                host = "localhost"
                port = fakePostgres.port
                database = "fake"
                user = "fake"
                password = "fake"
            }
            val connection = PgConnection.connect(vertx, options).coAwait()
            shouldThrow<Exception> {
                connection.query("select 1").execute().coAwait()
            }
        } finally {
            vertx.close().coAwait()
        }
    }

    Given("Vertx query executor and a fake postgres failing the first statement with admin shutdown") {
        // the client decodes the FATAL, tears the connection down and may fail the pending query with
        // either the decoded error or its closed-connection exception: classification must hold for both
        When("A real client connection runs a statement") {
            val thrown = FakePostgres.failingOnFirstStatement("57P01").use { fakePostgres ->
                failingStatement(fakePostgres)
            }

            Then("The wire-decoded failure classifies as ConnectionException") {
                executor.extractConnectionException(thrown).shouldBeTypeOf<ConnectionException>()
            }
        }
    }

    Given("Vertx query executor and a fake postgres dying on the first statement without a message") {
        When("A real client connection runs a statement") {
            val thrown = FakePostgres.closingOnFirstStatement().use { fakePostgres ->
                failingStatement(fakePostgres)
            }

            Then("The code-absent socket death classifies as ConnectionException") {
                executor.extractConnectionException(thrown).shouldBeTypeOf<ConnectionException>()
            }
        }
    }

    Given("A fake postgres refusing connections with too many connections") {
        When("A real client connects") {
            val thrown = FakePostgres.failingAtStartup("53300").use { fakePostgres ->
                val vertx = Vertx.vertx()
                try {
                    val options = PgConnectOptions().apply {
                        host = "localhost"
                        port = fakePostgres.port
                        database = "fake"
                        user = "fake"
                        password = "fake"
                    }
                    shouldThrow<Exception> {
                        PgConnection.connect(vertx, options).coAwait()
                    }
                } finally {
                    vertx.close().coAwait()
                }
            }

            Then("The refusal classifies as ConnectionException") {
                executor.extractConnectionException(thrown).shouldBeTypeOf<ConnectionException>()
            }
        }
    }
})
