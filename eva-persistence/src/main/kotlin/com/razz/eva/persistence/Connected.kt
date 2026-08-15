package com.razz.eva.persistence

/**
 * A connection and the facts about where it came from.
 *
 * The transaction manager builds this when it takes a connection from a pool, so the facts travel with the
 * connection instead of through a side channel. A statement that reuses a connection from the coroutine
 * context reads the same facts, so it reports the pool that opened the connection.
 *
 * This is the place to hang anything else that describes one connection, for example the thread that holds
 * it or the time the pool took to give it.
 */
data class Connected<C>(
    val value: C,
    val endpoint: DbEndpoint,
    val role: PoolRole?,
)
