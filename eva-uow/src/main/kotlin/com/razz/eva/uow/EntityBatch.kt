package com.razz.eva.uow

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.repository.EntityRepos
import com.razz.eva.repository.TransactionalContext

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
}
