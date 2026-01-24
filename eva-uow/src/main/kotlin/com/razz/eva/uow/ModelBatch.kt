package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.TransactionalContext

internal sealed interface ModelBatch {
    suspend fun persist(context: TransactionalContext, repos: ModelRepos): List<Model<*, *>>

    fun with(model: Model<*, *>): ModelBatch

    class Add<MID : ModelId<out Comparable<*>>, M : Model<MID, *>>(model: M) : ModelBatch {
        private val models = mutableListOf(model)

        override suspend fun persist(context: TransactionalContext, repos: ModelRepos): List<Model<*, *>> {
            return repos.repoFor(models.first()).add(context, models)
        }

        override fun with(model: Model<*, *>): Add<MID, M> {
            @Suppress("UNCHECKED_CAST")
            models.add(model as M)
            return this
        }
    }

    class Update<MID : ModelId<out Comparable<*>>, M : Model<MID, *>>(model: M) : ModelBatch {
        private val models = mutableListOf(model)

        override suspend fun persist(context: TransactionalContext, repos: ModelRepos): List<Model<*, *>> {
            return repos.repoFor(models.first()).update(context, models)
        }

        override fun with(model: Model<*, *>): Update<MID, M> {
            @Suppress("UNCHECKED_CAST")
            models.add(model as M)
            return this
        }
    }
}
