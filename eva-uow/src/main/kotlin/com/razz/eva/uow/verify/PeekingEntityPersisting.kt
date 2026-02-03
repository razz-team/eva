package com.razz.eva.uow.verify

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.uow.EntityPersisting
import kotlin.reflect.KClass

internal class PeekingEntityPersisting : EntityPersisting {

    private var currentEntity: CreatableEntity? = null
    private var currentKey: EntityKey<*>? = null

    override fun <E : CreatableEntity> add(entity: E) {
        require(currentEntity == null && currentKey == null)
        currentEntity = entity
    }

    override fun <E : DeletableEntity> delete(entity: E) {
        require(currentEntity == null && currentKey == null)
        currentEntity = entity
    }

    override fun <E : DeletableEntity, K : EntityKey<E>> delete(key: K, entityClass: KClass<E>) {
        require(currentEntity == null && currentKey == null)
        currentKey = key
    }

    fun peek(): CreatableEntity {
        val entity = requireNotNull(currentEntity)
        currentEntity = null
        return entity
    }

    fun peekKey(): EntityKey<*> {
        val key = requireNotNull(currentKey)
        currentKey = null
        return key
    }
}
