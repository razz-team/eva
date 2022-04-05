package com.razz.eva.migrations

import com.razz.eva.migrations.Migration.EventsMigration
import com.razz.eva.migrations.Migration.ModelsMigration
import org.flywaydb.core.Flyway

class Migrations(
    config: MigrationsDatabaseConfig,
    modelsMigration: ModelsMigration
) {
    private val flywayModels: Flyway = flywayProvider(config, modelsMigration)
    private val flywayEvents: Flyway = flywayProvider(config, EventsMigration)

    fun start() {
        flywayModels.migrate()
        flywayEvents.migrate()
    }

    companion object {

        private fun flywayProvider(config: MigrationsDatabaseConfig, migration: Migration): Flyway = Flyway
            .configure()
            // true by default in flyway 8.0.0
            // deprecated in favor of `flyway-teams` feature
            // default behavior may change in flyaway 9
            // .ignoreFutureMigrations(true)
            .dataSource(
                config.jdbcURL.toString(),
                config.ddlUser.toString(),
                config.ddlPassword.showPassword()
            )
            .schemas(migration.schema.toString())
            .locations(migration.classpathLocation())
            .load()
    }
}
