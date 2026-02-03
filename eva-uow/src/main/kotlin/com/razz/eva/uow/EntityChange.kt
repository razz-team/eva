package com.razz.eva.uow

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import kotlin.reflect.KClass

internal sealed interface EntityChange {
    fun persist(persisting: EntityPersisting)
}

internal data class AddEntity<E : CreatableEntity>(
    private val entity: E,
) : EntityChange {

    override fun persist(persisting: EntityPersisting) {
        persisting.add(entity)
    }
}

internal data class DeleteEntity<E : DeletableEntity>(
    private val entity: E,
) : EntityChange {

    override fun persist(persisting: EntityPersisting) {
        persisting.delete(entity)
    }
}

internal data class DeleteEntityByKey<E : DeletableEntity, K : EntityKey<E>>(
    private val key: K,
    private val entityClass: KClass<E>,
) : EntityChange {

    override fun persist(persisting: EntityPersisting) {
        persisting.delete(key, entityClass)
    }
}
