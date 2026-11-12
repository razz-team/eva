package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model

/** The models the failed attempt read with `model()`, for a guarded unit of work's `onFailure`. */
class FailedAttempt internal constructor(private val uowName: String) {

    /**
     * Returns the model the failed attempt read from [proven]. Throws if the attempt did not read it. Pass a field of
     * the params that `onFailure` received; a [Proven] from other params returns what the attempt that built those
     * params read.
     */
    operator fun <M : Model<*, *>> get(proven: Proven<*, M>): M = checkNotNull(proven.readOrNull()) {
        "$uowName: onFailure asked for ${proven.targetId}, and the failed attempt did not read that model with " +
            "model(). Ask only for a Proven the block reads on every path"
    }
}
