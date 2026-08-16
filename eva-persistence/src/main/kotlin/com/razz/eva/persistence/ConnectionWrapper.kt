package com.razz.eva.persistence

import kotlin.coroutines.CoroutineContext

interface ConnectionWrapper<T> : CoroutineContext.Element {

    val connection: T

    /** The pool that opened this connection, so a statement reusing it reports the truth. */
    val endpoint: DbEndpoint

    val role: PoolRole?

    suspend fun begin()

    suspend fun commit()

    suspend fun rollback()
}
