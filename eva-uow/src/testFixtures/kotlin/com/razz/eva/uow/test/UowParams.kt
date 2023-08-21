package com.razz.eva.uow.test

import com.razz.eva.serialization.json.JsonFormat.json
import com.razz.eva.uow.serialization.kotlinx.Serialization

interface UowParams<Params> : com.razz.eva.uow.serialization.kotlinx.UowParams<Params> {

    override fun encoder(): Serialization.Encoder? {
        return Serialization.Encoder(json)
    }
}
