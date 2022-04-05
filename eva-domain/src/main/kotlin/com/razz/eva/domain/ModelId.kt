package com.razz.eva.domain

interface ModelId<ID : Comparable<ID>> {
    val id: ID

    fun stringValue(): String = id.toString()
}
