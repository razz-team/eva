package com.razz.eva.uow

import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelEventDrive

internal class ModelEventDrive<E : ModelEvent<*>> internal constructor(
    private val events: List<E>,
) : ModelEventDrive<E> {

    constructor() : this(emptyList())

    override fun with(events: List<E>): ModelEventDrive<E> {
        return ModelEventDrive(this.events + events)
    }

    override fun events(): List<E> {
        return events
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as com.razz.eva.uow.ModelEventDrive<*>

        if (events != other.events) return false

        return true
    }

    override fun hashCode(): Int {
        return events.hashCode()
    }
}
