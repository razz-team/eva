package com.razz.eva.repository

import com.razz.eva.events.UowEvent

interface EventRepository {

    suspend fun warmup() {}

    suspend fun add(uowEvent: UowEvent)
}
