package com.razz.eva.test

import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Witness
import com.razz.eva.domain.mutating

/** A spec body that runs with a witness for every model id type. */
typealias WitnessedBody<S> = context(Witness<ModelId<out Comparable<*>>>) S.() -> Unit

/** Runs [body] on this spec with that witness. Call it from a spec base's init. */
fun <S> S.runWitnessed(body: WitnessedBody<S>): Unit = mutating { body() }
