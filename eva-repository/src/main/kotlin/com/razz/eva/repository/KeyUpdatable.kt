package com.razz.eva.repository

import com.razz.eva.domain.UpdatableEntity
import com.razz.eva.domain.EntityKey

@Suppress("INAPPLICABLE_JVM_NAME")
interface KeyUpdatable<E : UpdatableEntity, K : EntityKey<E>> : KeyDeletable<E, K> {

    suspend fun update(context: TransactionalContext, key: K): Boolean

    @JvmName("updateByKeys")
    suspend fun update(context: TransactionalContext, keys: List<K>): Int
}
