package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId

private fun existingChangeExceptionMessage(modelId: ModelId<*>) =
    "Change for a given model [$modelId] was already registered"

abstract class ChangesWithResult<R> {
    internal abstract val result: R
    internal abstract val toPersist: List<Change>
}

internal class ChangesWithoutResult private constructor(
    private val changes: Map<ModelId<out Comparable<*>>, Change>,
    private val emptyChangesAllowed: Boolean = false
) {

    constructor(emptyChangesAllowed: Boolean) : this(emptyMap(), emptyChangesAllowed)
    constructor() : this(emptyMap())

    fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withAdded(model: M): ChangesWithoutResult {
        val eventDrive = model.writeEvents(ModelEventDrive())
        return changes(model, eventDrive.events(), ::Add)
    }

    fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withUpdated(model: M): ChangesWithoutResult {
        if (model.isDirty()) {
            val eventDrive = model.writeEvents(ModelEventDrive())
            return changes(model, eventDrive.events(), ::Update)
        } else require(emptyChangesAllowed) {
            "Attempted to register unchanged model [${model.id()}] but empty changes were disallowed"
        }
        return this
    }

    fun <MID : ModelId<out Comparable<*>>, E : ModelEvent<MID>, M : Model<MID, E>>
    withUnchanged(model: M): ChangesWithoutResult {
        require(!model.isDirty() && !model.isNew()) {
            "Attempted mark ${if (model.isDirty()) "dirty" else "new"} model [${model.id()}] as unchanged"
        }
        return changes(model, emptyList()) { _, _ -> Noop }
    }

    fun <R> withResult(result: R): ChangesWithResult<R> {
        check(emptyChangesAllowed || changes.isNotEmpty()) { "No changes to persist" }
        return DefaultChangesWithResult(result, changes.values.toList())
    }

    private fun <E : ModelEvent<MID>, M : Model<MID, E>, MID : ModelId<out Comparable<*>>>
    changes(model: M, modelEvents: List<E>, changer: (M, List<E>) -> Change): ChangesWithoutResult {
        return when (changes[model.id()]) {
            null -> ChangesWithoutResult(
                LinkedHashMap(changes).apply {
                    put(model.id(), changer(model, modelEvents))
                },
                emptyChangesAllowed
            )
            else -> throw IllegalStateException(existingChangeExceptionMessage(model.id()))
        }
    }
}

internal class DefaultChangesWithResult<R>(
    override val result: R,
    override val toPersist: List<Change>
) : ChangesWithResult<R>()

internal object NoChanges : ChangesWithResult<Unit>() {
    override val result = Unit
    override val toPersist = emptyList<Change>()
}
