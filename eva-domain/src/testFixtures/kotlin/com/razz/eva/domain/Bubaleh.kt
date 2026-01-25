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
    modelState: ModelState<BubalehId, BubalehEvent>
) : Model<BubalehId, BubalehEvent>(id, modelState) {

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
        modelState: ModelState<BubalehId, BubalehEvent>
    ) : Bubaleh(id, employeeId, taste, producedOn, volume, modelState) {

        fun consume(): Consumed {
            return Consumed(
                id, employeeId, taste, producedOn, volume, raiseEvent(BubalehConsumed(id()))
            )
        }
    }

    class Consumed(
        id: BubalehId,
        employeeId: EmployeeId,
        taste: BubalehTaste,
        producedOn: Instant,
        volume: BubalehBottleVol,
        modelState: ModelState<BubalehId, BubalehEvent>
    ) : Bubaleh(id, employeeId, taste, producedOn, volume, modelState)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Bubaleh) return false

        if (id != other.id) return false
        if (employeeId != other.employeeId) return false
        if (taste != other.taste) return false
        if (producedOn != other.producedOn) return false
        if (volume != other.volume) return false
        if (this.state() != other.state()) return false
        if (this.version() != other.version()) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
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
