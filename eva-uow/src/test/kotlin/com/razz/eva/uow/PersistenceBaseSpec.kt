package com.razz.eva.uow

import com.razz.eva.migrations.Migration.ModelsMigration
import com.razz.eva.migrations.Migrations
import com.razz.eva.migrations.MigrationsDatabaseConfig
import com.razz.eva.persistence.config.DatabaseConfig
import com.razz.eva.persistence.config.DbName
import com.razz.eva.persistence.config.DbNodeAddress
import com.razz.eva.persistence.config.DbPassword
import com.razz.eva.persistence.config.DbUser
import com.razz.eva.persistence.config.JdbcURL
import com.razz.eva.persistence.config.MaxPoolSize
import com.razz.eva.test.db.DatabaseContainerHelper
import java.lang.Runtime.getRuntime

abstract class PersistenceBaseSpec(
    body: FuncTestSpec<TransactionalModule>.() -> Unit
) : FuncTestSpec<TransactionalModule>({ SHARED_APP_MODULE }, body) {

    companion object {
        val SHARED_APP_MODULE: TransactionalModule
            get() {
                val dbConfig = DatabaseConfig(
                    nodes = listOf(DbNodeAddress(DB.dbHost(), DB.dbPort())),
                    name = DbName(DB.dbName()),
                    user = DbUser(DB.username()),
                    password = DbPassword(DB.password()),
                    maxPoolSize = MaxPoolSize(10),
                    executorType = executorType
                )
                val migrationConfig = MigrationsDatabaseConfig(
                    jdbcURL = JdbcURL(DB.jdbcUrl()),
                    ddlUser = DbUser(DB.username()),
                    ddlPassword = DbPassword(DB.password())
                )
                Migrations(migrationConfig, ModelsMigration("com/razz/eva/test/db")).start()
                return TransactionalModule(dbConfig)
            }

        private val DB = DatabaseContainerHelper.create("eva")

        init {
            getRuntime().addShutdownHook(
                Thread {
                    SHARED_APP_MODULE.close()
                }
            )
        }
    }
}
