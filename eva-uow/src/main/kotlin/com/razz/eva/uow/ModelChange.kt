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

internal data class AddModel<MID : ModelId<out Comparable<*>>, M : Model<MID, *>, E : ModelEvent<MID>>(
    override val model: M,
    override val modelEvents: List<E>,
) : ModelChange {

    override val id: ModelId<out Comparable<*>> = model.id()

    override fun persist(persisting: ModelPersisting) {
        require(model.isNew()) {
            "Attempted to register ${if (model.isDirty()) "changed" else "unchanged"} " +
                "model [${model.id().stringValue()}] as new"
        }
        persisting.add(model)
    }
}

internal data class UpdateModel<MID : ModelId<out Comparable<*>>, M : Model<MID, *>, E : ModelEvent<MID>>(
    override val model: M,
    override val modelEvents: List<E>,
) : ModelChange {

    override val id: ModelId<out Comparable<*>> = model.id()

    override fun persist(persisting: ModelPersisting) {
        require(model.isDirty()) {
            "Attempted to register ${if (model.isNew()) "new" else "unchanged"} " +
                "model [${model.id().stringValue()}] as changed"
        }
        persisting.update(model)
    }
}

internal data class NoopModel(
    override val model: Model<*, *>,
) : ModelChange {

    override val id: ModelId<out Comparable<*>> = model.id()
    override fun persist(persisting: ModelPersisting) {
        require(model.isPersisted()) {
            "Attempted to register ${if (model.isNew()) "new" else "changed"} " +
                "model [${model.id().stringValue()}] as unchanged"
        }
    }
    override val modelEvents: List<ModelEvent<out ModelId<out Comparable<*>>>> = listOf()
}
