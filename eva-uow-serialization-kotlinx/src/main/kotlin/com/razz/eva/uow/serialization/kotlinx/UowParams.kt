package com.razz.eva.uow.serialization.kotlinx

import kotlinx.serialization.SerializationStrategy

interface UowParams<Params> : com.razz.eva.uow.UowParams<Params, Serialization.Encoder> {

    fun serialization(): SerializationStrategy<Params>

    override fun serializationStrategy(): Serialization.Strategy<Params> {
        return Serialization.Strategy { data, e -> e.formatter.encodeToString(serialization(), data) }
    }
}
