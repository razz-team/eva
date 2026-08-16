package com.razz.eva.persistence

class WithCtxConnectionTransactionManager(
    private val connection: () -> DummyConnection? = { null },
    private val connectionProvider: DummyConnectionProvider = DummyConnectionProvider(),
    private val endpoint: DbEndpoint = DbEndpoint("dummy", 5432, "dummy"),
    private val beforeTxn: (ConnectionMode) -> Unit = { },
    private val afterTxn: (ConnectionMode, Any) -> Unit = { _, _ -> },
    private val setPipelining: () -> Boolean = { true },
    private val wrapped: TransactionManager<*>? = null,
    private val afterFailedTransaction: suspend () -> Unit = { }
) : TransactionManager<DummyConnection>(connectionProvider, connectionProvider) {

    override suspend fun <R> inTransaction(mode: ConnectionMode, block: suspend (DummyConnection) -> R): R {
        beforeTxn(mode)
        try {
            val res = wrapped?.let {
                it.inTransaction(mode) { _ -> block(connectionProvider.acquire()) }
            } ?: block(connectionProvider.acquire())
            afterTxn(mode, res as Any)
            return res
        } catch (ex: Exception) {
            afterFailedTransaction()
            throw ex
        }
    }

    override fun wrapConnection(
        newConn: DummyConnection,
        endpoint: DbEndpoint,
        role: PoolRole?,
    ): ConnectionWrapper<DummyConnection> = DummyConnectionWrapper(newConn, endpoint, role)

    override suspend fun ctxConnection(): ConnectionWrapper<DummyConnection>? =
        connection()?.let { DummyConnectionWrapper(it, endpoint, PoolRole.PRIMARY) }

    override fun supportsPipelining() = setPipelining()
}

private class DummyConnectionWrapper(
    override val connection: DummyConnection,
    override val endpoint: DbEndpoint,
    override val role: PoolRole?,
) : ConnectionWrapper<DummyConnection> {
    override val key get() = Key
    companion object Key : kotlin.coroutines.CoroutineContext.Key<DummyConnectionWrapper>
    override suspend fun begin() = Unit
    override suspend fun commit() = Unit
    override suspend fun rollback() = Unit
}
