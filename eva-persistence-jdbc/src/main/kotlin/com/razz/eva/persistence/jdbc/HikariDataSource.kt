package com.razz.eva.persistence.jdbc

import com.razz.eva.persistence.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.Duration

fun dataSource(config: DatabaseConfig, isPrimary: Boolean): HikariDataSource =
    HikariConfig().run {
        idleTimeout = Duration.ofMinutes(1).toMillis()
        jdbcUrl = config.jdbcURL.toString() + if (isPrimary) {
            "?targetServerType=master"
        } else {
            "?targetServerType=preferSlave&loadBalanceHosts=true"
        }
        username = config.user.toString()
        password = config.password.showPassword()
        maximumPoolSize = config.maxPoolSize.value()
        leakDetectionThreshold = 3000L
        HikariDataSource(this)
    }