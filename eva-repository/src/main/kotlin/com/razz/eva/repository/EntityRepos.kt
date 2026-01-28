package com.razz.eva.repository

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import kotlin.reflect.KClass

class EntityRepos(
    vararg repos: ClassToRepo<*>,
) {
    data class ClassToRepo<E : CreatableEntity> internal constructor(
        val entityClass: KClass<out E>,
        val repository: EntityRepository<E>,
    ) {
        internal fun withSealedSubclasses(): List<ClassToRepo<out E>> = entityClass.sealedSubclasses
            .flatMap { sealedClass -> ClassToRepo(sealedClass, repository).withSealedSubclasses() }
            .plus(this)

        internal fun toPair() = entityClass to repository
    }

    private val classToRepo = repos
        .flatMap { it.withSealedSubclasses() }.associate { it.toPair() }

    fun <E : CreatableEntity> repoFor(entity: E): EntityRepository<E> {
        val repo = classToRepo[entity::class] ?: throw EntityRepositoryNotFoundException(entity)
        @Suppress("UNCHECKED_CAST")
        return repo as EntityRepository<E>
    }

    fun <E : DeletableEntity> deletableRepoFor(entity: E): DeletableEntityRepository<E> {
        val repo = classToRepo[entity::class] ?: throw EntityRepositoryNotFoundException(entity)
        if (repo !is DeletableEntityRepository<*>) {
            throw IllegalStateException("Repository for ${entity::class} does not support deletion")
        }
        @Suppress("UNCHECKED_CAST")
        return repo as DeletableEntityRepository<E>
    }
}

class EntityRepositoryNotFoundException(entity: CreatableEntity) :
    IllegalStateException("Repository is not found for entity: $entity")

infix fun <E : CreatableEntity, S : E> KClass<S>.hasEntityRepo(repository: EntityRepository<E>) =
    EntityRepos.ClassToRepo(this, repository)
