package com.razz.eva.test.db

import com.razz.eva.test.db.DockerImageName.PostgrePartmanImage14
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
    private val db: PostgreDockerContainer
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

    private fun localConn() = localPool(dbName, 4).connection

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
        db.close()
    }

    private class PostgreDockerContainer(
        imageName: DockerImageName
    ) : PostgreSQLContainer<PostgreDockerContainer>(imageName.toTestcontainers()), Startable {

        val additionalUrlParams: String = constructUrlParameters("?", "&")
    }

    companion object {

        private val DB_NAME_FORMAT = "[a-z0-9_]+".toRegex()
        private const val memoryInBytes = 2L * 1024 * 1024 * 1024

        // 5 cpu = 2 for db; 1 cpu = 1 for db; 16 cpu = 4 for db
        private val cpuCount = ceil(getRuntime().availableProcessors() / 4.0).toLong()

        private val pgContainer = PostgreDockerContainer(PostgrePartmanImage14)
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")
            .withCommand(
                "-c fsync=off" +
                    " -c full_page_writes=off" +
                    " -c synchronous_commit=off" +
                    " -c wal_level=minimal" +
                    " -c checkpoint_timeout=1d" +
                    " -c wal_init_zero=off" +
                    " -c max_wal_senders=0" +
                    " -c max_connections=200" +
                    " -c shared_buffers=512MB" +
                    " -c wal_buffers=16MB" +
                    " -c effective_cache_size=1536MB" +
                    " -c effective_io_concurrency=200" +
                    " -c maintenance_work_mem=128MB" +
                    " -c work_mem=1310kB" +
                    " -c max_worker_processes=2" +
                    " -c max_parallel_workers_per_gather=1" +
                    " -c max_parallel_workers=2" +
                    " -c max_parallel_maintenance_workers=1"
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

        fun localPool(dbName: String, size: Int) = synchronized(this) {
            localPools.getOrPut(dbName) {
                HikariConfig().run {
                    jdbcUrl = jdbcUrl(dbName, pgContainer)
                    username = "test"
                    password = "test"
                    maximumPoolSize = size
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

    object PostgrePartmanImage14 : DockerImageName("public.ecr.aws/t9u6q1l4/testdb:pg16")
}
