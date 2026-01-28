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

    suspend fun add(context: TransactionalContext, entity: E): E

    suspend fun add(context: TransactionalContext, entities: List<E>): List<E>
}

/**
 * Extended repository for entities that support deletion.
 */
interface DeletableEntityRepository<E : DeletableEntity> : EntityRepository<E> {

    suspend fun delete(context: TransactionalContext, entity: E): Boolean

    suspend fun delete(context: TransactionalContext, entities: List<E>): Int
}
