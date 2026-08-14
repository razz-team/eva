package com.razz.eva.persistence

import com.razz.eva.persistence.config.DatabaseConfig

/**
 * Where a [ConnectionProvider] points, for the `server.address`, `server.port` and `db.namespace` span
 * attributes.
 *
 * A provider serves one pool, so this identifies the pool. It does not identify the server that ran the
 * statement. Behind a connection pooler such as pgcat that is the useful distinction anyway: it separates
 * primary traffic from replica traffic, which a per-statement address cannot.
 */
data class DbEndpoint(
    val address: String,
    val port: Int,
    val database: String,
) {
    companion object {
        /**
         * A config may list several nodes. Only a single node yields a meaningful address, so a multi node
         * config reports the joined host list and the first port, which stays honest about pointing at a
         * set. It does not invent one member of it.
         */
        fun of(config: DatabaseConfig): DbEndpoint = DbEndpoint(
            address = config.nodes.joinToString(",") { it.host() },
            port = config.nodes.first().port(),
            database = config.name.toString(),
        )
    }
}
