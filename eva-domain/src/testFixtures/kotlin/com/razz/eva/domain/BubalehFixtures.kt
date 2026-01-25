package com.razz.eva.domain

import com.razz.eva.domain.BubalehBottleVol.THIRTY_THREE
import com.razz.eva.domain.BubalehTaste.SWEET
import com.razz.eva.domain.ModelState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Version.Companion.V1
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID.randomUUID

object BubalehFixtures {

    private val clock = Clock.tickMillis(UTC)

    fun aServedBubaleh(
        id: BubalehId = BubalehId(randomUUID()),
        employeeId: EmployeeId = EmployeeId(randomUUID()),
        taste: BubalehTaste = SWEET,
        producedOn: Instant = clock.instant(),
        volume: BubalehBottleVol = THIRTY_THREE
    ) = Bubaleh.Served(
        id,
        employeeId = employeeId,
        taste = taste,
        producedOn = producedOn,
        volume = volume,
        persistentState(V1, null),
    )

    fun aConsumedBubaleh(
        id: BubalehId = BubalehId(randomUUID()),
        employeeId: EmployeeId = EmployeeId(randomUUID()),
        taste: BubalehTaste = SWEET,
        producedOn: Instant = clock.instant(),
        volume: BubalehBottleVol = THIRTY_THREE
    ) = Bubaleh.Consumed(
        id,
        employeeId = employeeId,
        taste = taste,
        producedOn = producedOn,
        volume = volume,
        persistentState(V1, null),
    )
}