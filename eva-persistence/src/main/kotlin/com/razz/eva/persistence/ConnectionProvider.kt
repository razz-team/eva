package com.razz.eva.persistence

interface ConnectionProvider<C> {

    /** Where this provider's pool points. Reported on query spans so a trace shows which pool served it. */
    val endpoint: DbEndpoint

    suspend fun acquire(): C

    suspend fun release(connection: C)
}
