package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId

internal sealed interface ModelChange {
    fun persist(persisting: ModelPersisting)

    val model: Model<*, *>
    val modelEvents: List<ModelEvent<out ModelId<out Comparable<*>>>>
    val id: ModelId<out Comparable<*>>
}

internal data class AddModel<MID : ModelId<out Comparable<*>>, M : Model<MID, *>>(
    override val model: M,
) : ModelChange {

    override val id: ModelId<out Comparable<*>> = model.id()
    override val modelEvents: List<ModelEvent<out ModelId<out Comparable<*>>>> get() = model.modelEvents()

    override fun persist(persisting: ModelPersisting) {
        persisting.add(model)
    }
}

internal data class UpdateModel<MID : ModelId<out Comparable<*>>, M : Model<MID, *>>(
    override val model: M,
) : ModelChange {

    override val id: ModelId<out Comparable<*>> = model.id()
    override val modelEvents: List<ModelEvent<out ModelId<out Comparable<*>>>> get() = model.modelEvents()

    override fun persist(persisting: ModelPersisting) {
        persisting.update(model)
    }
}

internal data class NoopModel(
    override val model: Model<*, *>,
) : ModelChange {

    override val id: ModelId<out Comparable<*>> = model.id()

    // The DSLs check this when they register a claim, but stubChanges claims every model its result reaches,
    // new and changed ones included. A claim a parent does not extend reaches here, and must not pass silently.
    override fun persist(persisting: ModelPersisting) {
        require(model.isPersisted()) {
            "Attempted to register ${if (model.isNew()) "new" else "changed"} " +
                "model [${model.id().stringValue()}] as unchanged"
        }
    }
    override val modelEvents: List<ModelEvent<out ModelId<out Comparable<*>>>> = listOf()
}
