package com.razz.eva.repository

import com.razz.eva.uow.UowEvent
import com.razz.eva.uow.params.UowParams

interface EventRepository {

    suspend fun warmup() {}

    suspend fun <P : UowParams<P>> add(uowEvent: UowEvent<P>)
}
