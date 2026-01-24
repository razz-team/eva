package com.razz.eva.repository

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import kotlin.reflect.KClass

class ModelRepos(
    vararg repos: ClassToRepo<*>,
) {
    data class ClassToRepo<M : Model<*, *>> internal constructor(
        val modelClass: KClass<out M>,
        val repository: ModelRepository<*, M>,
    ) {
        internal fun withSealedSubclasses(): List<ClassToRepo<out M>> = modelClass.sealedSubclasses
            .flatMap { sealedClass -> ClassToRepo(sealedClass, repository).withSealedSubclasses() }
            .plus(this)

        internal fun toPair() = modelClass to repository
    }

    private val classToRepo = repos
        .flatMap { it.withSealedSubclasses() }.associate { it.toPair() }

    fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> repoFor(model: M): ModelRepository<ID, M> {
        val repo = classToRepo[model::class] ?: throw RepositoryNotFoundException(model)
        @Suppress("UNCHECKED_CAST")
        return repo as ModelRepository<ID, M>
    }
}

class RepositoryNotFoundException(model: Model<*, *>) :
    IllegalStateException("Repository is not found for model: $model")

infix fun <M : Model<*, *>, S : M> KClass<S>.hasRepo(repository: ModelRepository<*, M>) =
    ModelRepos.ClassToRepo(this, repository)
