package com.razz.eva.uow.test

import com.razz.eva.serialization.json.JsonFormat.json
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.serialization.kotlinx.Serialization

object Encoders : (UowParams<*, *>) -> com.razz.eva.uow.Serialization.Encoder {

    override fun invoke(p: UowParams<*, *>): com.razz.eva.uow.Serialization.Encoder = when (p) {
        is com.razz.eva.uow.serialization.kotlinx.UowParams<*> -> Serialization.Encoder(json)
        else -> com.razz.eva.uow.Serialization.Encoder.NOOP
    }
}
