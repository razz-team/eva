package com.razz.eva.repository

import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey

@Suppress("INAPPLICABLE_JVM_NAME")
interface KeyDeletable<E : DeletableEntity, K : EntityKey<E>> {

    suspend fun delete(context: TransactionalContext, key: K): Boolean

    @JvmName("deleteByKeys")
    suspend fun delete(context: TransactionalContext, keys: List<K>): Int
}
