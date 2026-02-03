package com.razz.eva.uow

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.repository.EntityRepos
import com.razz.eva.repository.TransactionalContext
import kotlin.reflect.KClass

internal sealed interface EntityBatch {
    suspend fun persist(context: TransactionalContext, repos: EntityRepos)

    fun with(entity: CreatableEntity): EntityBatch

    class Add<E : CreatableEntity>(entity: E) : EntityBatch {
        private val entities = mutableListOf(entity)

        override suspend fun persist(context: TransactionalContext, repos: EntityRepos) {
            repos.repoFor(entities.first()).add(context, entities)
        }

        override fun with(entity: CreatableEntity): Add<E> {
            @Suppress("UNCHECKED_CAST")
            entities.add(entity as E)
            return this
        }
    }

    class Delete<E : DeletableEntity>(entity: E) : EntityBatch {
        private val entities = mutableListOf(entity)

        override suspend fun persist(context: TransactionalContext, repos: EntityRepos) {
            repos.deletableRepoFor(entities.first()).delete(context, entities)
        }

        override fun with(entity: CreatableEntity): Delete<E> {
            @Suppress("UNCHECKED_CAST")
            entities.add(entity as E)
            return this
        }
    }

    class DeleteByKey<E : DeletableEntity, K : EntityKey<E>>(
        key: K,
        private val entityClass: KClass<E>,
    ) : EntityBatch {
        private val keys = mutableListOf(key)

        override suspend fun persist(context: TransactionalContext, repos: EntityRepos) {
            repos.keyDeletableRepoFor(entityClass).delete(context, keys)
        }

        override fun with(entity: CreatableEntity): DeleteByKey<E, K> {
            throw UnsupportedOperationException(
                "Cannot add entity to key-based deletion batch. Use withKey() instead.",
            )
        }

        @Suppress("UNCHECKED_CAST")
        fun withKey(key: EntityKey<*>): DeleteByKey<E, K> {
            keys.add(key as K)
            return this
        }
    }
}
