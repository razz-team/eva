package com.razz.eva.test.domain

import com.razz.eva.domain.ModelState
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Version.Companion.V1

fun <ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>> persistentStateV1() =
    ModelState.PersistentState.persistentState<ID, E>(V1, null)
