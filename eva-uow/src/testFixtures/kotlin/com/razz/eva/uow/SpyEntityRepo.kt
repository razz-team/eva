package com.razz.eva.uow

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.domain.UpdatableEntity
import com.razz.eva.repository.DeletableEntityRepository
import com.razz.eva.repository.EntityRepository
import com.razz.eva.repository.KeyDeletable
import com.razz.eva.repository.KeyUpdatable
import com.razz.eva.repository.TransactionalContext
import com.razz.eva.repository.UpdatableEntityRepository
import com.razz.eva.uow.ExecutionStep.EntitiesAdded
import com.razz.eva.uow.ExecutionStep.EntitiesDeleted
import com.razz.eva.uow.ExecutionStep.EntitiesDeletedByKey
import com.razz.eva.uow.ExecutionStep.EntitiesUpdated
import com.razz.eva.uow.ExecutionStep.EntitiesUpdatedByKey
import com.razz.eva.uow.ExecutionStep.EntityAdded
import com.razz.eva.uow.ExecutionStep.EntityDeleted
import com.razz.eva.uow.ExecutionStep.EntityDeletedByKey
import com.razz.eva.uow.ExecutionStep.EntityUpdated
import com.razz.eva.uow.ExecutionStep.EntityUpdatedByKey

@Suppress("INAPPLICABLE_JVM_NAME")
class SpyKeyDeletableEntityRepo<E : DeletableEntity, K : EntityKey<E>>(
    private val history: MutableList<ExecutionStep>,
) : DeletableEntityRepository<E>, KeyDeletable<E, K> {

    override suspend fun add(context: TransactionalContext, entity: E): E {
        history.add(EntityAdded(context, entity))
        return entity
    }

    override suspend fun add(context: TransactionalContext, entities: List<E>): List<E> {
        history.add(EntitiesAdded(context, entities))
        return entities
    }

    override suspend fun delete(context: TransactionalContext, entity: E): Boolean {
        history.add(EntityDeleted(context, entity))
        return true
    }

    override suspend fun delete(context: TransactionalContext, entities: List<E>): Int {
        history.add(EntitiesDeleted(context, entities))
        return entities.size
    }

    override suspend fun delete(context: TransactionalContext, key: K): Boolean {
        history.add(EntityDeletedByKey(context, key))
        return true
    }

    @JvmName("deleteByKeys")
    override suspend fun delete(context: TransactionalContext, keys: List<K>): Int {
        history.add(EntitiesDeletedByKey(context, keys))
        return keys.size
    }
}

@Suppress("INAPPLICABLE_JVM_NAME")
class SpyKeyUpdatableEntityRepo<E : UpdatableEntity, K : EntityKey<E>>(
    private val history: MutableList<ExecutionStep>,
) : UpdatableEntityRepository<E>, KeyUpdatable<E, K> {

    override suspend fun add(context: TransactionalContext, entity: E): E {
        history.add(EntityAdded(context, entity))
        return entity
    }

    override suspend fun add(context: TransactionalContext, entities: List<E>): List<E> {
        history.add(EntitiesAdded(context, entities))
        return entities
    }

    override suspend fun delete(context: TransactionalContext, entity: E): Boolean {
        history.add(EntityDeleted(context, entity))
        return true
    }

    override suspend fun delete(context: TransactionalContext, entities: List<E>): Int {
        history.add(EntitiesDeleted(context, entities))
        return entities.size
    }

    override suspend fun delete(context: TransactionalContext, key: K): Boolean {
        history.add(EntityDeletedByKey(context, key))
        return true
    }

    @JvmName("deleteByKeys")
    override suspend fun delete(context: TransactionalContext, keys: List<K>): Int {
        history.add(EntitiesDeletedByKey(context, keys))
        return keys.size
    }

    override suspend fun update(context: TransactionalContext, entity: E): Boolean {
        history.add(EntityUpdated(context, entity))
        return true
    }

    override suspend fun update(context: TransactionalContext, entities: List<E>): Int {
        history.add(EntitiesUpdated(context, entities))
        return entities.size
    }

    override suspend fun update(context: TransactionalContext, key: K): Boolean {
        history.add(EntityUpdatedByKey(context, key))
        return true
    }

    override suspend fun update(context: TransactionalContext, keys: List<K>): Int {
        history.add(EntitiesUpdatedByKey(context, keys))
        return keys.size
    }
}

class SpyCreatableEntityRepo(
    private val history: MutableList<ExecutionStep>,
) : EntityRepository<CreatableEntity> {

    override suspend fun add(context: TransactionalContext, entity: CreatableEntity): CreatableEntity {
        history.add(EntityAdded(context, entity))
        return entity
    }

    override suspend fun add(context: TransactionalContext, entities: List<CreatableEntity>): List<CreatableEntity> {
        history.add(EntitiesAdded(context, entities))
        return entities
    }
}
