package com.razz.eva.persistence.postgres

object PgHelpers {
    const val PG_UNIQUE_VIOLATION = "23505"

    /**
     * Sql states outside class 08 which still mean the connection is unavailable: the three states
     * a postgres restart produces plus a full connection slot table. Deliberately not the whole
     * class 57: `57014 query_canceled` is a statement timeout or a user cancel, the connection
     * survives it and a retry on a healthy connection would misclassify it.
     */
    val PG_CONNECTION_UNAVAILABLE = setOf(
        "57P01", // admin_shutdown
        "57P02", // crash_shutdown
        "57P03", // cannot_connect_now
        "53300", // too_many_connections
    )
}
