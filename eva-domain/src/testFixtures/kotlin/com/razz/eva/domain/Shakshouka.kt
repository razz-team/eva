package com.razz.eva.domain

import com.razz.eva.domain.ShakshoukaEvent.ShakshoukaConsumed
import com.razz.eva.domain.ShakshoukaState.CONSUMED
import com.razz.eva.domain.ShakshoukaState.SERVED
import java.util.*

data class ShakshoukaId(override val id: UUID = UUID.randomUUID()) : ModelId<UUID>

enum class EggsCount { FOUR, FIVE }

enum class ShakshoukaState {
    SERVED, CONSUMED
}

sealed class Shakshouka(
    val id: ShakshoukaId,
    val employeeId: EmployeeId,
    val eggsCount: EggsCount,
    val withPita: Boolean,
    entityState: EntityState<ShakshoukaId, ShakshoukaEvent>
) : Model<ShakshoukaId, ShakshoukaEvent>(id, entityState) {

    fun state(): ShakshoukaState = when (this) {
        is Served -> SERVED
        is Consumed -> CONSUMED
    }

    class Served(
        id: ShakshoukaId,
        employeeId: EmployeeId,
        eggsCount: EggsCount,
        withPita: Boolean,
        entityState: EntityState<ShakshoukaId, ShakshoukaEvent>
    ) : Shakshouka(id, employeeId, eggsCount, withPita, entityState) {

        fun consume(): Consumed {
            return Consumed(
                id,
                employeeId,
                eggsCount,
                withPita,
                entityState().raiseEvent(ShakshoukaConsumed(id))
            )
        }
    }

    class Consumed(
        id: ShakshoukaId,
        employeeId: EmployeeId,
        eggsCount: EggsCount,
        withPita: Boolean,
        entityState: EntityState<ShakshoukaId, ShakshoukaEvent>
    ) : Shakshouka(id, employeeId, eggsCount, withPita, entityState)
}

sealed class ShakshoukaEvent(
    override val modelId: ShakshoukaId
) : ModelEvent<ShakshoukaId> {

    override val modelName = Shakshouka::class.simpleName!!

    data class ShakshoukaCreated(
        val shakshoukaId: ShakshoukaId,
        val employeeId: EmployeeId,
        val eggsCount: EggsCount,
        val withPita: Boolean,
    ) : ShakshoukaEvent(shakshoukaId), ModelCreatedEvent<ShakshoukaId>

    data class ShakshoukaConsumed(
        val shakshoukaId: ShakshoukaId
    ) : ShakshoukaEvent(shakshoukaId), ModelEvent<ShakshoukaId>
}
