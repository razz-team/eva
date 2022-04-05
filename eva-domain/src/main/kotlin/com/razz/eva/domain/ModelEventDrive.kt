package com.razz.eva.domain

interface ModelEventDrive<E : ModelEvent<*>> {

    fun with(events: List<E>): ModelEventDrive<E>

    fun events(): List<E>
}
