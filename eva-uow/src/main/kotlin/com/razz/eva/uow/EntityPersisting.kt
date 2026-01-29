package com.razz.eva.uow

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import kotlin.reflect.KClass

internal interface EntityPersisting {

    fun <E : CreatableEntity> add(entity: E)

    fun <E : DeletableEntity> delete(entity: E)

    fun <E : DeletableEntity, K : EntityKey<E>> delete(key: K, entityClass: KClass<E>)
}
