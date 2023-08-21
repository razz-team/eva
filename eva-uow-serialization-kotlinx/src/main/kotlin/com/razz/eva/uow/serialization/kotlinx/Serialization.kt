package com.razz.eva.uow.serialization.kotlinx

import kotlinx.serialization.StringFormat

object Serialization {
    data class Encoder(val formatter: StringFormat) : com.razz.eva.uow.Serialization.Encoder
    fun interface Strategy<T> : com.razz.eva.uow.Serialization.Strategy<T, Encoder> {
        override operator fun invoke(data: T, encoder: Encoder): String
    }
}
