package com.razz.eva.test.repository

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import com.razz.eva.persistence.TransactionManager
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.TransactionalContext
import java.time.Clock
import java.time.Instant

class WritableModelRepository(
    private val txnManager: TransactionManager<*>,
    private val clock: Clock,
    private val modelRepos: ModelRepos
) {

    suspend fun <R> inTransaction(
        txStartedAt: Instant,
        block: suspend (context: TransactionalContext) -> R
    ): R {
        val context = TransactionalContext.transactionalContext(txStartedAt)
        val txBlock: suspend () -> R = {
            block(context)
        }
        return txnManager.inTransaction(REQUIRE_NEW, txBlock)
    }

    suspend fun <ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>, M : Model<ID, E>> add(
        model: M,
        txStartedAt: Instant = clock.instant()
    ): M =
        inTransaction(txStartedAt) {
            modelRepos.repoFor(model).add(it, model)
        }

    suspend fun <ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>, M : Model<ID, E>> update(
        model: M,
        txStartedAt: Instant = clock.instant()
    ): M =
        inTransaction(txStartedAt) {
            modelRepos.repoFor(model).update(it, model)
        }
}
