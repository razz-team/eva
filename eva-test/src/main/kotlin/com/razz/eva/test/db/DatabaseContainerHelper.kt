package com.razz.eva.test.db

import mu.KotlinLogging
import org.testcontainers.containers.PostgreSQLContainer
import java.io.Closeable
import java.sql.SQLException
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.DAYS

class DatabaseContainerHelper private constructor(
    dbPrefix: String,
    private val db: DatabaseContainer
) : Closeable {
    private val logger = KotlinLogging.logger {}

    private val dbName: String

    init {
        val now = ZonedDateTime.now()
        val sinceMonth = Duration.between(now.truncatedTo(DAYS).withDayOfMonth(1), now)
        val seconds = sinceMonth.toSeconds()
        val sortableSuffix = System.getenv("RAZZ_TEST_DB_CONTAINER_DB_SUFFIX")
            ?: "${seconds / 60}_${seconds % 60}"
        dbName = dbPrefix + "_" + sortableSuffix
    }

    fun dbName() = dbName

    fun dbHost() = db.pgContainer.host

    fun dbPort() = db.pgContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)

    fun jdbcUrl() = jdbcUrl(dbName)

    private fun jdbcUrl(dbName: String) = db.jdbcUrl(dbName)

    fun username(): String = db.pgContainer.username

    fun password(): String = db.pgContainer.password

    private fun localConn() = db.localPool(dbName, 4).connection

    private fun createDb() = try {
        db.managementPool.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                        CREATE DATABASE $dbName;
                    """.trimIndent()
                )
            }
        }
    } catch (e: SQLException) {
        // DUPLICATE DATABASE ; there is no CREATE DATABASE IF NOT EXISTS in postgres
        // also sometimes it could throw
        // ERROR: duplicate key value violates unique constraint "pg_database_datname_index"
        // which is a postgres bug according to google
        if (e.sqlState == "42P04") {
            logger.debug(e) { "42P04" }
        } else {
            logger.error(e) { "Most likely pg_database_datname_index" }
        }
    }

    fun createSchemas(createPartman: Boolean = true) {
        val statement = if (createPartman) {
            """
                CREATE SCHEMA IF NOT EXISTS partman;
                CREATE EXTENSION IF NOT EXISTS pg_partman WITH SCHEMA partman;
            """.trimIndent()
        } else {
            ""
        } +
            """
                CREATE EXTENSION IF NOT EXISTS btree_gist;
                CREATE EXTENSION IF NOT EXISTS intarray;
                CREATE EXTENSION IF NOT EXISTS timescaledb;
                CREATE EXTENSION IF NOT EXISTS pg_trgm;
                CREATE EXTENSION IF NOT EXISTS unaccent;
                CREATE EXTENSION IF NOT EXISTS hstore;
                CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;
            """.trimIndent()
        check(statement.isNotBlank())
        localConn().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(statement)
            }
        }
    }

    override fun close() {
        db.pgContainer.close()
    }

    companion object {

        private val DB_NAME_FORMAT = "[a-z0-9_]+".toRegex()

        fun create(dbName: String, databaseContainer: DatabaseContainer): DatabaseContainerHelper {
            require(DB_NAME_FORMAT.matches(dbName)) {
                "Wrong DB name: $dbName. DB name should have only lowercase chars and numbers."
            }
            return DatabaseContainerHelper(dbName, databaseContainer).apply {
                createDb()
                createSchemas()
            }
        }
    }
}
