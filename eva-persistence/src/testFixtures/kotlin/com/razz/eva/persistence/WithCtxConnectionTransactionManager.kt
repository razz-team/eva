package com.razz.eva.persistence

class WithCtxConnectionTransactionManager(
    private val connection: () -> DummyConnection? = { null },
    private val connectionProvider: DummyConnectionProvider = DummyConnectionProvider(),
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
        } catch (e: Exception) {
            afterFailedTransaction()
            throw e
        }
    }

    override fun wrapConnection(newConn: DummyConnection): ConnectionWrapper<DummyConnection> {
        TODO("NEVER CALLED")
    }

    override suspend fun ctxConnection(): DummyConnection? {
        return connection()
    }

    override fun supportsPipelining() = setPipelining()
}
