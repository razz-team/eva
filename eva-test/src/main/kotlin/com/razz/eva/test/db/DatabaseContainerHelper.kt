package com.razz.eva.test.db

import com.razz.eva.test.db.DockerImageName.PostgrePartmanImage13
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.testcontainers.containers.PostgreSQLContainer
import java.io.Closeable
import java.lang.Runtime.getRuntime
import java.sql.SQLException
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.DAYS
import kotlin.math.ceil
import org.testcontainers.utility.DockerImageName as TestcontainersDockerImageName

class DatabaseContainerHelper private constructor(
    dbPrefix: String,
    private val db: PostgreDockerContainer,
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

    fun dbHost() = db.host

    fun dbPort() = db.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)

    fun jdbcUrl() = jdbcUrl(dbName)

    fun jdbcUrl(dbName: String) = jdbcUrl(dbName, db)

    fun username(): String = db.username

    fun password(): String = db.password

    private fun localConn() = localPool(dbName).connection

    private fun createDb() = try {
        managementPool.connection.use { conn ->
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
            """.trimIndent()
        check(statement.isNotBlank())
        localConn().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(statement)
            }
        }
    }

    override fun close() {
        db.close()
    }

    private class PostgreDockerContainer(
        imageName: DockerImageName
    ) : PostgreSQLContainer<PostgreDockerContainer>(imageName.toTestcontainers()), Startable {

        val additionalUrlParams: String = constructUrlParameters("?", "&")
    }

    companion object {

        private val DB_NAME_FORMAT = "[a-z0-9_]+".toRegex()
        private const val memoryInBytes = 1L * 1024 * 1024 * 1024

        // 5 cpu = 2 for db; 1 cpu = 1 for db; 16 cpu = 4 for db
        private val cpuCount = ceil(getRuntime().availableProcessors() / 4.0).toLong()

        private val pgContainer = PostgreDockerContainer(PostgrePartmanImage13)
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")
            .withCommand(
                "-c fsync=off" +
                    " -c synchronous_commit=off" +
                    " -c wal_level=minimal" +
                    " -c max_wal_senders=0" +
                    " -c wal_init_zero=off" +
                    " -c full_page_writes=off" +
                    " -c checkpoint_timeout=1d" +
                    " -c max_wal_size=2GB" +
                    " -c max_connections=300" +
                    " -c shared_buffers=512MB" +
                    " -c temp_buffers=32MB" +
                    " -c wal_buffers=32MB" +
                    " -c seq_page_cost=0.01" +
                    " -c random_page_cost=0.01" +
                    " -c effective_cache_size=64MB" +
                    " -c maintenance_work_mem=512MB" +
                    " -c work_mem=64MB"
            )
            .withTmpFs(mapOf("/var/lib/postgresql/data" to "rw"))
            .withReuse(true)
            .withCreateContainerCmdModifier { cmd ->
                cmd.hostConfig
                    ?.withMemory(memoryInBytes)
                    ?.withCpuCount(cpuCount)
            }.apply { start() }

        private val managementPool = HikariConfig().run {
            jdbcUrl = pgContainer.jdbcUrl
            username = "test"
            password = "test"
            maximumPoolSize = 2
            HikariDataSource(this)
        }

        private val localPools = mutableMapOf<String, HikariDataSource>()

        private fun jdbcUrl(dbName: String, db: PostgreDockerContainer): String {
            return "jdbc:postgresql://${db.host}:" +
                "${db.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)}/$dbName${db.additionalUrlParams}"
        }

        fun localPool(dbName: String) = synchronized(this) {
            localPools.getOrPut(dbName) {
                HikariConfig().run {
                    jdbcUrl = jdbcUrl(dbName, pgContainer)
                    username = "test"
                    password = "test"
                    maximumPoolSize = 10
                    initializationFailTimeout = -1
                    HikariDataSource(this)
                }
            }
        }

        fun create(dbName: String): DatabaseContainerHelper {
            require(DB_NAME_FORMAT.matches(dbName)) {
                "Wrong DB name: $dbName. DB name should have only lowercase chars and numbers."
            }
            return DatabaseContainerHelper(dbName, pgContainer).apply {
                createDb()
                createSchemas()
            }
        }
    }
}

sealed class DockerImageName(internal val value: String) {
    fun toTestcontainers(): TestcontainersDockerImageName = TestcontainersDockerImageName.parse(this.value)
        .asCompatibleSubstituteFor("postgres")

    object PostgrePartmanImage13 : DockerImageName("alecx/testdb_pg13:v1.0-amd64")
}
