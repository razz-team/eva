package com.razz.eva.domain

class TestDrive<E : ModelEvent<*>>(val events: List<E> = listOf()) : ModelEventDrive<E> {

    override fun with(events: List<E>): ModelEventDrive<E> {
        return TestDrive(this.events + events)
    }

    override fun events(): List<E> {
        return events
    }
}
