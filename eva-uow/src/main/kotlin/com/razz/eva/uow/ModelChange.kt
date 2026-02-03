package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId

internal sealed interface ModelChange {
    fun persist(persisting: ModelPersisting)
    fun merge(succ: ModelChange): ModelChange?

    val modelEvents: List<ModelEvent<out ModelId<out Comparable<*>>>>
    val id: ModelId<out Comparable<*>>
}

internal data class AddModel<MID : ModelId<out Comparable<*>>, M : Model<MID, *>, E : ModelEvent<MID>>(
    private val model: M,
    override val modelEvents: List<E>,
) : ModelChange {

    override val id: ModelId<out Comparable<*>> = model.id()

    override fun persist(persisting: ModelPersisting) {
        require(model.isNew()) {
            "Attempted to register ${if (model.isDirty()) "changed" else "unchanged"} model [${model.id()}] as new"
        }
        persisting.add(model)
    }

    override fun merge(succ: ModelChange): ModelChange? = when (succ) {
        is AddModel<*, *, *> -> null
        is UpdateModel<*, *, *> -> if (!succ.sameVersion(model)) {
            null
        } else if (succ.modelEvents isSuccessorOf modelEvents) {
            succ.toAddModel()
        } else {
            null
        }
        is NoopModel -> this
    }
}

internal data class UpdateModel<MID : ModelId<out Comparable<*>>, M : Model<MID, *>, E : ModelEvent<MID>>(
    private val model: M,
    override val modelEvents: List<E>,
) : ModelChange {

    override val id: ModelId<out Comparable<*>> = model.id()

    override fun persist(persisting: ModelPersisting) {
        require(model.isDirty()) {
            "Attempted to register ${if (model.isNew()) "new" else "unchanged"} model [${model.id()}] as changed"
        }
        persisting.update(model)
    }

    fun sameVersion(other: Model<*, *>) = model.id() == other.id() && model.version() == other.version()

    fun toAddModel(): AddModel<MID, M, E> = AddModel(model, modelEvents)

    override fun merge(succ: ModelChange): ModelChange? = when (succ) {
        is AddModel<*, *, *> -> null
        is UpdateModel<*, *, *> -> if (!succ.sameVersion(model)) {
            null
        } else if (succ.modelEvents isSuccessorOf modelEvents) {
            succ
        } else {
            null
        }
        is NoopModel -> this
    }
}

internal data class NoopModel(
    private val model: Model<*, *>,
) : ModelChange {

    override val id: ModelId<out Comparable<*>> = model.id()
    override fun persist(persisting: ModelPersisting) {
        require(model.isPersisted()) {
            "Attempted to register ${if (model.isNew()) "new" else "changed"} model [${model.id()}] as unchanged"
        }
    }
    override val modelEvents: List<ModelEvent<out ModelId<out Comparable<*>>>> = listOf()
    override fun merge(succ: ModelChange): ModelChange = succ
}

private infix fun <E : ModelEvent<out ModelId<out Comparable<*>>>> List<E>
    .isSuccessorOf(modelEvents: List<E>): Boolean {
    if (this.size <= modelEvents.size) {
        return false
    }
    modelEvents.forEachIndexed { i, e ->
        val succ = this[i]
        if (succ !== e) {
            return false
        }
    }
    return true
}
