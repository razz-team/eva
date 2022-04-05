package com.razz.eva.persistence

interface ConnectionProvider<C> {

    suspend fun acquire(): C

    suspend fun release(connection: C)
}
