package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId

internal sealed interface Change {
    fun persist(persisting: ModelPersisting)
    val modelEvents: List<ModelEvent<out ModelId<out Comparable<*>>>>
}

internal data class Add<MID : ModelId<out Comparable<*>>, M : Model<MID, *>, E : ModelEvent<MID>>(
    private val model: M,
    override val modelEvents: List<E>
) : Change {

    init {
        require(model.isNew()) { "Can't add non-new model" }
    }

    override fun persist(persisting: ModelPersisting) {
        persisting.add(model)
    }
}

internal data class Update<MID : ModelId<out Comparable<*>>, M : Model<MID, *>, E : ModelEvent<MID>>(
    private val model: M,
    override val modelEvents: List<E>
) : Change {

    init {
        require(model.isDirty()) { "Can't update non-dirty model" }
    }

    override fun persist(persisting: ModelPersisting) {
        persisting.update(model)
    }
}

internal object Noop : Change {

    override fun persist(persisting: ModelPersisting) = Unit
    override val modelEvents: List<ModelEvent<out ModelId<out Comparable<*>>>> = emptyList()
}
