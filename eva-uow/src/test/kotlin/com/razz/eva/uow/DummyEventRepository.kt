package com.razz.eva.uow

import com.razz.eva.repository.EventRepository
import com.razz.eva.uow.params.UowParams

class DummyEventRepository(val addInterceptor: (UowEvent<*>) -> Unit = {}) : EventRepository {

    override suspend fun <P : UowParams<P>> add(uowEvent: UowEvent<P>) = addInterceptor(uowEvent)
}
