package com.razz.eva.uow.params.kotlinx

import kotlinx.serialization.SerializationStrategy

interface UowParams<Params> : com.razz.eva.uow.UowParams<Params> {
    fun serialization(): SerializationStrategy<Params>
}
