package com.razz.eva.uow.func

import com.razz.eva.migrations.Migration.Factory.modelsMigration
import com.razz.eva.migrations.Migrations
import com.razz.eva.migrations.MigrationsDatabaseConfig
import com.razz.eva.persistence.config.DatabaseConfig
import com.razz.eva.persistence.config.DbName
import com.razz.eva.persistence.config.DbNodeAddress
import com.razz.eva.persistence.config.DbPassword
import com.razz.eva.persistence.config.DbUser
import com.razz.eva.persistence.config.ExecutorType
import com.razz.eva.persistence.config.JdbcURL
import com.razz.eva.persistence.config.MaxPoolSize
import com.razz.eva.test.db.DatabaseContainer
import com.razz.eva.test.db.DatabaseContainerHelper

abstract class PersistenceBaseSpec(
    body: FunctionalSpec<TestModule>.() -> Unit
) : FunctionalSpec<TestModule>({ sharedModule }, body) {

    companion object {
        private val∑ DB = DatabaseContainerHelper.create("eva", DatabaseContainer.BASIC)

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
        val migrations = Migrations(migrationConfig, modelsMigration("com/razz/eva/test/db")).apply {
            start()
        }

        val sharedModule = TestModule(dbConfig)

        val executorType: ExecutorType
            get() = System
                .getenv("PRIMARY_EXECUTOR_TYPE")
                ?.runCatching(ExecutorType::valueOf)
                ?.getOrNull()
                ?: ExecutorType.JDBC
    }
}
