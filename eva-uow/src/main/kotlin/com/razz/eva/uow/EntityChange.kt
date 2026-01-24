package com.razz.eva.uow

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity

internal sealed interface EntityChange {
    fun persist(persisting: EntityPersisting)

    val entity: CreatableEntity
}

internal data class AddEntity<E : CreatableEntity>(
    override val entity: E,
) : EntityChange {

    override fun persist(persisting: EntityPersisting) {
        persisting.addEntity(entity)
    }
}

internal data class DeleteEntity<E : DeletableEntity>(
    override val entity: E,
) : EntityChange {

    override fun persist(persisting: EntityPersisting) {
        persisting.deleteEntity(entity)
    }
}
