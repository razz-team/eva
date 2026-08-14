package com.razz.eva.persistence

import com.razz.eva.persistence.ConnectionMode.REQUIRE_EXISTING
import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

private val primaryEndpoint = DbEndpoint("primary.db", 5432, "eva")
private val replicaEndpoint = DbEndpoint("replica.db", 5432, "eva")

private object NoopWrapper : ConnectionWrapper<DummyConnection> {
    override val key: CoroutineContext.Key<*> get() = Key
    object Key : CoroutineContext.Key<NoopWrapper>
    override suspend fun begin() = Unit
    override suspend fun commit() = Unit
    override suspend fun rollback() = Unit
}

/**
 * Inherits the real withConnection and inTransaction, unlike WithCtxConnectionTransactionManager which
 * overrides them, so these cover the choice the shipped manager actually makes.
 */
private class CtxConnectionManager : TransactionManager<DummyConnection>(
    DummyConnectionProvider(primaryEndpoint),
    DummyConnectionProvider(replicaEndpoint),
) {
    override fun wrapConnection(newConn: DummyConnection) = NoopWrapper
    override suspend fun ctxConnection() = DummyConnection
    override fun supportsPipelining() = false
}

private class FailingProvider(override val endpoint: DbEndpoint) : ConnectionProvider<DummyConnection> {
    override suspend fun acquire(): DummyConnection = throw IllegalStateException("pool exhausted")
    override suspend fun release(connection: DummyConnection) = Unit
}

private class FailingPoolManager : TransactionManager<DummyConnection>(
    FailingProvider(primaryEndpoint),
    FailingProvider(replicaEndpoint),
) {
    override fun wrapConnection(newConn: DummyConnection) = NoopWrapper
    override suspend fun ctxConnection(): DummyConnection? = null
    override fun supportsPipelining() = false
}

private class TwoPoolManager : TransactionManager<DummyConnection>(
    DummyConnectionProvider(primaryEndpoint),
    DummyConnectionProvider(replicaEndpoint),
) {
    override fun wrapConnection(newConn: DummyConnection) = NoopWrapper
    override suspend fun ctxConnection(): DummyConnection? = null
    override fun supportsPipelining() = false
}

/**
 * The pool an executor reports on a span comes from this slot, so a drift between the pool the manager
 * chooses and the one it records would mislabel every span. These cover that choice.
 */
class AcquiredEndpointSpec : FunSpec({

    test("withConnection records the replica by default") {
        val slot = AcquiredEndpoint()
        withContext(slot) { TwoPoolManager().withConnection { } }
        slot.endpoint shouldBe replicaEndpoint
        slot.role shouldBe PoolRole.REPLICA
    }

    test("withConnection records the primary when the flag demands it") {
        val slot = AcquiredEndpoint()
        withContext(slot + PrimaryConnectionRequiredFlag) { TwoPoolManager().withConnection { } }
        slot.endpoint shouldBe primaryEndpoint
        slot.role shouldBe PoolRole.PRIMARY
    }

    test("inTransaction records the primary even where a read would have taken the replica") {
        val slot = AcquiredEndpoint()
        withContext(slot) { TwoPoolManager().inTransaction(REQUIRE_NEW) { } }
        slot.endpoint shouldBe primaryEndpoint
        slot.role shouldBe PoolRole.PRIMARY
    }

    test("nothing is recorded when no connection is acquired") {
        val slot = AcquiredEndpoint()
        shouldThrow<IllegalStateException> {
            withContext(slot) { TwoPoolManager().inTransaction(REQUIRE_EXISTING) { } }
        }
        slot.endpoint shouldBe null
    }

    test("reusing a context connection records the primary that opened it") {
        val slot = AcquiredEndpoint()
        withContext(slot) { CtxConnectionManager().withConnection { } }
        slot.endpoint shouldBe primaryEndpoint
        slot.role shouldBe PoolRole.PRIMARY
    }

    test("a statement inside a transaction records the primary") {
        val slot = AcquiredEndpoint()
        withContext(slot) { CtxConnectionManager().inTransaction(REQUIRE_EXISTING) { } }
        slot.endpoint shouldBe primaryEndpoint
        slot.role shouldBe PoolRole.PRIMARY
    }

    test("the pool is named even when the connection is never obtained") {
        val slot = AcquiredEndpoint()
        shouldThrow<PersistenceException.ConnectionException> {
            withContext(slot) { FailingPoolManager().withConnection { } }
        }
        slot.endpoint shouldBe replicaEndpoint
        slot.role shouldBe PoolRole.REPLICA
    }
})
