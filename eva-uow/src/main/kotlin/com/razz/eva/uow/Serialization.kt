package com.razz.eva.uow

object Serialization {
    interface Encoder {
        object NOOP : Encoder
    }
    fun interface Strategy<T, E : Encoder> {
        operator fun invoke(data: T, encoder: E): String
    }
}
