package com.razz.eva.persistence.jdbc.executor

import com.razz.eva.persistence.FakePostgres
import com.razz.eva.persistence.PersistenceException.ConnectionException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.mockk
import org.jooq.SQLDialect.POSTGRES
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.postgresql.util.PSQLException
import java.sql.DriverManager

class JdbcConnectionFailureWireSpec : BehaviorSpec({

    Given("Jdbc query executor and a fake postgres failing the first statement with admin shutdown") {
        val executor = JdbcQueryExecutor(mockk(relaxed = true))

        When("A real driver connection runs a statement") {
            val extracted = FakePostgres.failingOnFirstStatement("57P01").use { fakePostgres ->
                val thrown = shouldThrow<DataAccessException> {
                    DriverManager.getConnection(fakePostgres.jdbcUrl()).use { connection ->
                        DSL.using(connection, POSTGRES).fetch("select 1")
                    }
                }
                executor.extractConnectionException(thrown)
            }

            Then("The wire-decoded failure classifies as ConnectionException") {
                extracted.shouldBeTypeOf<ConnectionException>()
            }
        }
    }

    Given("Jdbc query executor and a fake postgres dying on the first statement without a message") {
        val executor = JdbcQueryExecutor(mockk(relaxed = true))

        When("A real driver connection runs a statement") {
            val extracted = FakePostgres.closingOnFirstStatement().use { fakePostgres ->
                val thrown = shouldThrow<DataAccessException> {
                    DriverManager.getConnection(fakePostgres.jdbcUrl()).use { connection ->
                        DSL.using(connection, POSTGRES).fetch("select 1")
                    }
                }
                executor.extractConnectionException(thrown)
            }

            Then("The code-absent socket death classifies as ConnectionException") {
                extracted.shouldBeTypeOf<ConnectionException>()
            }
        }
    }

    Given("A fake postgres refusing connections with too many connections") {

        When("A real driver connects") {
            val thrown = FakePostgres.failingAtStartup("53300").use { fakePostgres ->
                shouldThrow<PSQLException> {
                    DriverManager.getConnection(fakePostgres.jdbcUrl())
                }
            }

            Then("The driver surfaces the sql state from the wire") {
                thrown.sqlState shouldBe "53300"
            }
        }
    }
})
