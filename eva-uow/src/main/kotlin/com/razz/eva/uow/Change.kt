package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId

internal sealed interface Change {
    fun persist(persisting: ModelPersisting)
    fun merge(succ: Change): Change?

    val modelEvents: List<ModelEvent<out ModelId<out Comparable<*>>>>
    val id: ModelId<out Comparable<*>>
}

internal data class Add<MID : ModelId<out Comparable<*>>, M : Model<MID, *>, E : ModelEvent<MID>>(
    private val model: M,
    override val modelEvents: List<E>
) : Change {

    override val id: ModelId<out Comparable<*>> = model.id()

    override fun persist(persisting: ModelPersisting) {
        require(model.isNew()) {
            "Attempted to register ${if (model.isDirty()) "changed" else "unchanged"} model [${model.id()}] as new"
        }
        persisting.add(model)
    }

    override fun merge(succ: Change): Change? = when (succ) {
        is Add<*, *, *> -> null
        is Update<*, *, *> -> if (!succ.sameVersion(model)) {
            null
        } else if (succ.modelEvents isSuccessorOf modelEvents) {
            succ.unwrap()
        } else {
            null
        }
        is Noop -> this
    }
}

internal data class Update<MID : ModelId<out Comparable<*>>, M : Model<MID, *>, E : ModelEvent<MID>>(
    private val model: M,
    override val modelEvents: List<E>
) : Change {

    override val id: ModelId<out Comparable<*>> = model.id()

    override fun persist(persisting: ModelPersisting) {
        require(model.isDirty()) {
            "Attempted to register ${if (model.isNew()) "new" else "unchanged"} model [${model.id()}] as changed"
        }
        persisting.update(model)
    }

    fun unwrap() = if (model.isNew()) Add(model, modelEvents) else Update(model, modelEvents)

    fun sameVersion(other: Model<*, *>) = model.id() == other.id() && model.version() == other.version()

    override fun merge(succ: Change): Change? = when (succ) {
        is Add<*, *, *> -> null
        is Update<*, *, *> -> if (!succ.sameVersion(model)) {
            null
        } else if (succ.modelEvents isSuccessorOf modelEvents) {
            succ
        } else {
            null
        }
        is Noop -> this
    }
}

internal data class Noop(
    private val model: Model<*, *>,
) : Change {

    override val id: ModelId<out Comparable<*>> = model.id()
    override fun persist(persisting: ModelPersisting) {
        require(model.isPersisted()) {
            "Attempted to register ${if (model.isNew()) "new" else "changed"} model [${model.id()}] as unchanged"
        }
    }
    override val modelEvents: List<ModelEvent<out ModelId<out Comparable<*>>>> = emptyList()
    override fun merge(succ: Change): Change = succ
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
