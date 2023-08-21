package com.razz.eva.uow.test

import com.razz.eva.events.UowEvent
import com.razz.eva.repository.EventRepository

class DummyEventRepository(val addInterceptor: (UowEvent) -> Unit = {}) : EventRepository {

    override suspend fun add(uowEvent: UowEvent) = addInterceptor(uowEvent)
}
