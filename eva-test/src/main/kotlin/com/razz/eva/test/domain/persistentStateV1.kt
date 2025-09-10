package com.razz.eva.test.domain

import com.razz.eva.domain.EntityState
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Version.Companion.V1

fun <ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>> persistentStateV1() =
    EntityState.PersistentState.persistentState<ID, E>(V1, Unit)
