package com.razz.eva.test.db

import com.razz.eva.test.db.DockerImageName.PostgrePartmanImage16
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT
import java.lang.Runtime.getRuntime
import kotlin.math.ceil
import org.testcontainers.utility.DockerImageName as TestcontainersDockerImageName

data class DatabaseContainer(
    val pgContainer: PostgreDockerContainer,
) {

    private val localPools = mutableMapOf<String, HikariDataSource>()

    val managementPool = HikariConfig().run {
        jdbcUrl = pgContainer.jdbcUrl
        username = "test"
        password = "test"
        maximumPoolSize = 2
        HikariDataSource(this)
    }

    fun localPool(dbName: String, size: Int) = synchronized(pgContainer) {
        localPools.getOrPut(dbName) {
            HikariConfig().run {
                jdbcUrl = jdbcUrl(dbName)
                username = "test"
                password = "test"
                maximumPoolSize = size
                initializationFailTimeout = -1
                HikariDataSource(this)
            }
        }
    }

    fun jdbcUrl(dbName: String): String {
        return "jdbc:postgresql://${pgContainer.host}:" +
            "${pgContainer.getMappedPort(POSTGRESQL_PORT)}/$dbName${pgContainer.additionalUrlParams}"
    }

    companion object {
        private const val MEMORY_IN_BYTES = 2L * 1024 * 1024 * 1024

        // 5 cpu = 2 for db; 1 cpu = 1 for db; 16 cpu = 4 for db
        private val cpuCount = ceil(getRuntime().availableProcessors() / 4.0).toLong()

        private val pgContainer = PostgreDockerContainer(PostgrePartmanImage16)
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
                    ?.withMemory(MEMORY_IN_BYTES)
                    ?.withCpuCount(cpuCount)
            }.apply { start() }

        val BASIC = DatabaseContainer(pgContainer)
    }

    class PostgreDockerContainer(
        imageName: DockerImageName
    ) : PostgreSQLContainer<PostgreDockerContainer>(imageName.toTestcontainers()), Startable {

        val additionalUrlParams: String = constructUrlParameters("?", "&")
    }
}

open class DockerImageName(internal val value: String) {
    fun toTestcontainers(): TestcontainersDockerImageName = TestcontainersDockerImageName.parse(this.value)
        .asCompatibleSubstituteFor("postgres")

    object PostgrePartmanImage16 : DockerImageName("public.ecr.aws/t9u6q1l4/testdb:16.6-bullseye-pt-5.1.0-ts-2.15.3")
}
