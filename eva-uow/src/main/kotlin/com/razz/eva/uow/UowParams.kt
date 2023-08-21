package com.razz.eva.uow

import com.razz.eva.IdempotencyKey

interface UowParams<Params, E : Serialization.Encoder> {
    val idempotencyKey: IdempotencyKey?
        get() = null

    fun encoder(): E? = null

    fun serializationStrategy(): Serialization.Strategy<Params, E>
}
