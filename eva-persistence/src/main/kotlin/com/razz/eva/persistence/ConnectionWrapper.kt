package com.razz.eva.persistence

import kotlin.coroutines.CoroutineContext

interface ConnectionWrapper<T> : CoroutineContext.Element {

    suspend fun begin()

    suspend fun commit()

    suspend fun rollback()
}
