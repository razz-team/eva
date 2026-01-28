package com.razz.eva.uow

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.repository.DeletableEntityRepository
import com.razz.eva.repository.EntityRepository
import com.razz.eva.repository.TransactionalContext
import com.razz.eva.uow.ExecutionStep.EntitiesAdded
import com.razz.eva.uow.ExecutionStep.EntitiesDeleted
import com.razz.eva.uow.ExecutionStep.EntityAdded
import com.razz.eva.uow.ExecutionStep.EntityDeleted

class SpyDeletableEntityRepo(
    private val history: MutableList<ExecutionStep>,
) : DeletableEntityRepository<DeletableEntity> {

    override suspend fun add(context: TransactionalContext, entity: DeletableEntity): DeletableEntity {
        history.add(EntityAdded(context, entity))
        return entity
    }

    override suspend fun add(context: TransactionalContext, entities: List<DeletableEntity>): List<DeletableEntity> {
        history.add(EntitiesAdded(context, entities))
        return entities
    }

    override suspend fun delete(context: TransactionalContext, entity: DeletableEntity): Boolean {
        history.add(EntityDeleted(context, entity))
        return true
    }

    override suspend fun delete(context: TransactionalContext, entities: List<DeletableEntity>): Int {
        history.add(EntitiesDeleted(context, entities))
        return entities.size
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
