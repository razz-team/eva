package com.razz.eva.uow.verify

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.uow.EntityPersisting

internal class PeekingEntityPersisting : EntityPersisting {

    private var currentEntity: CreatableEntity? = null

    override fun <E : CreatableEntity> add(entity: E) {
        require(currentEntity == null)
        currentEntity = entity
    }

    override fun <E : DeletableEntity> delete(entity: E) {
        require(currentEntity == null)
        currentEntity = entity
    }

    fun peek(): CreatableEntity {
        val entity = requireNotNull(currentEntity)
        currentEntity = null
        return entity
    }
}
