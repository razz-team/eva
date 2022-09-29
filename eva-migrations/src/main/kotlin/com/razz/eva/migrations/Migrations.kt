package com.razz.eva.migrations

import com.razz.eva.migrations.Migration.Factory.EventsMigration
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

class Migrations(
    config: MigrationsDatabaseConfig,
    mainMigration: Migration,
    vararg otherMigrations: Migration = arrayOf(EventsMigration),
) {
    private val flywayMigrators: List<Flyway> = listOf(mainMigration, *otherMigrations).map { migration ->
        flywayProvider(config, migration)
    }

    fun start() {
        flywayMigrators.forEach(Flyway::migrate)
    }

    companion object {

        private fun flywayProvider(config: MigrationsDatabaseConfig, migration: Migration): Flyway = Flyway
            .configure()
            // true by default in flyway 8.0.0
            // deprecated in favor of `flyway-teams` feature
            // default behavior may change in flyaway 9
            // .ignoreFutureMigrations(true)
            .dataSource(
                HikariDataSource(
                    HikariConfig().apply {
                        jdbcUrl = config.jdbcURL.toString()
                        username = config.ddlUser.toString()
                        password = config.ddlPassword.showPassword()
                        maximumPoolSize = 2 // need at least two for pg advisory lock and ddl connection
                    }
                )
            )
            .schemas(migration.schema.toString())
            .locations(migration.classpathLocation())
            .load()
    }
}
