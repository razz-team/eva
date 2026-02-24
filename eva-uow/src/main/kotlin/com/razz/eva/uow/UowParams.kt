package com.razz.eva.uow

import com.razz.eva.IdempotencyKey

interface UowParams<Params> {
    val idempotencyKey: IdempotencyKey?
        get() = null
}
