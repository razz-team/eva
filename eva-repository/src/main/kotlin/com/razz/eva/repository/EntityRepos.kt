package com.razz.eva.repository

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
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

    fun <E : DeletableEntity, K : EntityKey<E>> keyDeletableRepoFor(entityClass: KClass<E>): KeyDeletable<E, K> {
        val repo = classToRepo[entityClass] ?: throw EntityRepositoryNotFoundException(entityClass)
        if (repo !is KeyDeletable<*, *>) {
            throw IllegalStateException(
                "Repository for $entityClass does not support key-based deletion. " +
                    "Implement KeyDeletable interface to enable this feature.",
            )
        }
        @Suppress("UNCHECKED_CAST")
        return repo as KeyDeletable<E, K>
    }
}

class EntityRepositoryNotFoundException : IllegalStateException {
    constructor(entity: CreatableEntity) : super("Repository is not found for entity: $entity")
    constructor(entityClass: KClass<*>) : super("Repository is not found for entity class: $entityClass")
}

infix fun <E : CreatableEntity, S : E> KClass<S>.hasEntityRepo(repository: EntityRepository<E>) =
    EntityRepos.ClassToRepo(this, repository)
