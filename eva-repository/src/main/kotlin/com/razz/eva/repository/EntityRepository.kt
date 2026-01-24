package com.razz.eva.repository

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity

/**
 * Repository interface for Entity persistence.
 *
 * Unlike [ModelRepository], entities:
 * - Have no explicit ID for lookup
 * - Can only be added (insert) or deleted, not updated
 * - Identity is determined by content
 */
interface EntityRepository<E : CreatableEntity> {

    /**
     * Insert a new entity.
     */
    suspend fun add(context: TransactionalContext, entity: E): E

    /**
     * Batch insert entities.
     */
    suspend fun add(context: TransactionalContext, entities: List<E>): List<E>
}

/**
 * Extended repository for entities that support deletion.
 */
interface DeletableEntityRepository<E : DeletableEntity> : EntityRepository<E> {

    /**
     * Delete an entity. Returns true if entity was found and deleted.
     */
    suspend fun delete(context: TransactionalContext, entity: E): Boolean

    /**
     * Batch delete entities. Returns count of deleted entities.
     */
    suspend fun delete(context: TransactionalContext, entities: List<E>): Int
}
