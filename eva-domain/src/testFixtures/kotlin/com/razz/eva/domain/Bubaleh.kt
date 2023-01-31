package com.razz.eva.domain

import com.razz.eva.domain.BubalehEvent.BubalehConsumed
import com.razz.eva.domain.BubalehState.CONSUMED
import com.razz.eva.domain.BubalehState.SERVED
import java.time.Instant
import java.util.*

data class BubalehId(override val id: UUID) : ModelId<UUID> {
    override fun toString(): String = id.toString()

    companion object {
        fun fromString(id: String) = BubalehId(UUID.fromString(id))
    }
}

enum class BubalehTaste { SWEET, SWEEEET, VERY_SWEET }

enum class BubalehBottleVol { THIRTY_THREE, OUGH_FIVE }

enum class BubalehState {
    SERVED, CONSUMED
}

sealed class Bubaleh(
    val id: BubalehId,
    val employeeId: EmployeeId,
    val taste: BubalehTaste,
    val producedOn: Instant,
    val volume: BubalehBottleVol,
    entityState: EntityState<BubalehId, BubalehEvent>
) : Model<BubalehId, BubalehEvent>(id, entityState) {

    fun state(): BubalehState = when (this) {
        is Served -> SERVED
        is Consumed -> CONSUMED
    }

    class Served(
        id: BubalehId,
        employeeId: EmployeeId,
        taste: BubalehTaste,
        producedOn: Instant,
        volume: BubalehBottleVol,
        entityState: EntityState<BubalehId, BubalehEvent>
    ) : Bubaleh(id, employeeId, taste, producedOn, volume, entityState) {

        fun consume(): Consumed {
            return Consumed(
                id, employeeId, taste, producedOn, volume, entityState().raiseEvent(BubalehConsumed(id()))
            )
        }
    }

    class Consumed(
        id: BubalehId,
        employeeId: EmployeeId,
        taste: BubalehTaste,
        producedOn: Instant,
        volume: BubalehBottleVol,
        entityState: EntityState<BubalehId, BubalehEvent>
    ) : Bubaleh(id, employeeId, taste, producedOn, volume, entityState)
}

sealed class BubalehEvent(
    override val modelId: BubalehId
) : ModelEvent<BubalehId> {

    override val modelName = Bubaleh::class.simpleName!!

    data class BubalehCreated(
        val bubalehId: BubalehId,
        val employeeId: EmployeeId,
        val taste: BubalehTaste,
        val producedOn: Instant,
        val volume: BubalehBottleVol,
    ) : BubalehEvent(bubalehId), ModelCreatedEvent<BubalehId>

    data class BubalehConsumed(
        val bubalehId: BubalehId
    ) : BubalehEvent(bubalehId), ModelEvent<BubalehId>
}
