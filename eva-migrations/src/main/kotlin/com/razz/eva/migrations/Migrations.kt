package com.razz.eva.migrations

import com.razz.eva.migrations.Migration.Factory.EventsMigration
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

class Migrations(
    private val dataSource: HikariDataSource,
    mainMigration: Migration,
    vararg otherMigrations: Migration = arrayOf(EventsMigration),
) : AutoCloseable {

    constructor(
        config: MigrationsDatabaseConfig,
        mainMigration: Migration,
        vararg otherMigrations: Migration = arrayOf(EventsMigration),
    ) : this(
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.jdbcURL.toString()
                username = config.ddlUser.toString()
                password = config.ddlPassword.showPassword()
                maximumPoolSize = 2 // need at least two for pg advisory lock and ddl connection
            },
        ),
        mainMigration,
        *otherMigrations,
    )

    private val flywayMigrators: List<Flyway> = listOf(mainMigration, *otherMigrations).map { migration ->
        flywayProvider(migration, dataSource)
    }

    fun start(withRepair: Boolean = false) {
        flywayMigrators.forEach { flyway ->
            if (withRepair) flyway.repair()
            flyway.migrate()
        }
    }

    override fun close() {
        dataSource.close()
    }

    companion object {

        private fun flywayProvider(
            migration: Migration,
            dataSource: DataSource,
        ): Flyway = Flyway
            .configure()
            // true by default in flyway 8.0.0
            // deprecated in favor of `flyway-teams` feature
            // default behavior may change in flyaway 9
            // .ignoreFutureMigrations(true)
            .dataSource(dataSource)
            .schemas(migration.schema.stringValue())
            .createSchemas(migration.schema.createOnMigration()) // Flyway defaults to true
            .locations(*migration.classpathLocations().toTypedArray())
            .load()
    }
}
