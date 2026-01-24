package com.razz.eva.migrations

import com.razz.eva.persistence.config.DbPassword
import com.razz.eva.persistence.config.DbUser
import com.razz.eva.persistence.config.JdbcURL

data class MigrationsDatabaseConfig(
    val jdbcURL: JdbcURL,
    val ddlUser: DbUser,
    val ddlPassword: DbPassword,
)
