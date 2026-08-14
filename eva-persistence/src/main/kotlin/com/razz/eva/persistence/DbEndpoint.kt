package com.razz.eva.persistence

import com.razz.eva.persistence.config.DatabaseConfig

/**
 * Where a [ConnectionProvider] points, for the `server.address`, `server.port` and `db.namespace` span
 * attributes.
 *
 * A provider serves one pool, so this identifies the pool rather than the server that ultimately ran the
 * statement. Behind a connection pooler such as pgcat that is the useful distinction anyway: it separates
 * primary traffic from replica traffic, which a per-statement address cannot.
 */
data class DbEndpoint(
    val address: String,
    val port: Int,
    val database: String,
    val role: Role,
) {
    /**
     * Which pool this is. A deployment may point both pools at the same host, so the address alone does
     * not always separate reads from writes; the role always does.
     */
    enum class Role { PRIMARY, REPLICA }

    companion object {
        /**
         * A config may list several nodes. Only a single node yields a meaningful address, so a multi node
         * config reports the joined host list and the first port, which stays honest about pointing at a
         * set rather than inventing one member of it.
         */
        fun of(config: DatabaseConfig, role: Role): DbEndpoint = DbEndpoint(
            address = config.nodes.joinToString(",") { it.host() },
            port = config.nodes.first().port(),
            database = config.name.toString(),
            role = role,
        )
    }
}
