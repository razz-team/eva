package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model

/** What a failed attempt decided, for a guarded unit of work's `onFailure`: each model as the attempt read it. */
class Decided internal constructor(private val uowName: String) {

    /** The model [proven] decided in the failed attempt. Refuses one the attempt never read: it was not decided. */
    operator fun <M : Model<*, *>> get(proven: Proven<*, M>): M = checkNotNull(proven.decidedOrNull()) {
        "$uowName: a failed write is answered only on a model its attempt decided, and ${proven.targetId} was not"
    }
}
