package com.razz.eva.uow

import java.time.Clock
import java.time.Instant
import java.time.Instant.now
import java.time.ZoneOffset.UTC

object Clocks {
    /**
     * Similar semantics to systemUTC
     * clock with fixed timestamp
     */
    fun fixedUTC(fixedInstant: Instant = now()): Clock = Clock.fixed(fixedInstant, UTC)

    /**
     * Similar semantics to systemUTC
     * clock with zero nanos
     */
    fun millisUTC(): Clock = Clock.tickMillis(UTC)

    /**
     * Conventional ticking clock to be used by apps
     * current implementation is [com.razz.kotlin.Clocks.millisUTC]
     */
    fun appTicking(): Clock = millisUTC()

    /**
     * Similar semantics to systemUTC
     * clock with zero millis
     */
    fun secondsUTC(): Clock = Clock.tickSeconds(UTC)
}
