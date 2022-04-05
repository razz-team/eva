package com.razz.eva.uow.params

import com.razz.eva.IdempotencyKey
import kotlinx.serialization.SerializationStrategy

interface UowParams<Params> {
    val idempotencyKey: IdempotencyKey?
        get() = null

    fun serialization(): SerializationStrategy<Params>
}
