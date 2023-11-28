package com.razz.eva.persistence.vertx

import com.razz.eva.persistence.ConnectionWrapper
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.pgclient.PgConnection
import io.vertx.sqlclient.Transaction
import kotlin.coroutines.CoroutineContext

internal class VertxConnectionElement(
    internal val connection: PgConnection
) : ConnectionWrapper<PgConnection> {

    private var transaction: Transaction? = null

    override suspend fun begin() {
        transaction = connection.begin().coAwait()
    }

    override suspend fun commit() {
        val txn = checkNotNull(transaction)
        txn.commit().coAwait()
        transaction = null
    }

    override suspend fun rollback() {
        val txn = checkNotNull(transaction)
        txn.rollback().coAwait()
        transaction = null
    }

    companion object Key : CoroutineContext.Key<VertxConnectionElement>
    override val key: CoroutineContext.Key<VertxConnectionElement>
        get() = VertxConnectionElement
}
