package com.razz.eva.test.repository

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import com.razz.eva.persistence.TransactionManager
import com.razz.eva.repository.EntityRepos
import com.razz.eva.repository.TransactionalContext
import java.time.Clock
import java.time.Instant

class WritableEntityRepository(
    private val txnManager: TransactionManager<*>,
    private val clock: Clock,
    private val entityRepos: EntityRepos,
) {

    suspend fun <R> inTransaction(
        txStartedAt: Instant,
        block: suspend (context: TransactionalContext) -> R,
    ): R {
        val context = TransactionalContext.transactionalContext(txStartedAt)
        val txBlock: suspend () -> R = {
            block(context)
        }
        return txnManager.inTransaction(REQUIRE_NEW, txBlock)
    }

    suspend fun <E : CreatableEntity> add(
        entity: E,
        txStartedAt: Instant = clock.instant(),
    ): E = inTransaction(txStartedAt) {
        entityRepos.repoFor(entity).add(it, entity)
    }

    suspend fun <E : CreatableEntity> add(
        entities: List<E>,
        txStartedAt: Instant = clock.instant(),
    ): List<E> = inTransaction(txStartedAt) { context ->
        entityRepos.repoFor(entities.first()).add(context, entities)
    }

    suspend fun <E : DeletableEntity> delete(
        entity: E,
        txStartedAt: Instant = clock.instant(),
    ): Boolean = inTransaction(txStartedAt) {
        entityRepos.deletableRepoFor(entity).delete(it, entity)
    }

    suspend fun <E : DeletableEntity> delete(
        entities: List<E>,
        txStartedAt: Instant = clock.instant(),
    ): Int = inTransaction(txStartedAt) { context ->
        entityRepos.deletableRepoFor(entities.first()).delete(context, entities)
    }
}
