package com.razz.eva.uow

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity

internal interface EntityPersisting {

    fun <E : CreatableEntity> addEntity(entity: E)

    fun <E : DeletableEntity> deleteEntity(entity: E)
}
