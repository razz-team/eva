package com.razz.eva.domain

interface Identifiable<ID : ModelId<out Comparable<*>>> {
    fun id(): ID
}
