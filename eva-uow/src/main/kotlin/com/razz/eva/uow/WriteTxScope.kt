package com.razz.eva.uow

/**
 * When the write transaction opens relative to [BaseUnitOfWork.tryPerform].
 */
enum class WriteTxScope {
    /**
     * Default. Reads inside [BaseUnitOfWork.tryPerform] run on the primary, un-transacted, each on its own
     * pooled connection released per statement; the write transaction opens only for the flush.
     * Minimal connection hold: write consistency comes from the version check, not a shared read snapshot.
     */
    FLUSH,

    /**
     * Opt-in. One transaction on one pooled connection spans [BaseUnitOfWork.tryPerform] and the flush;
     * reads join it through the connection in the coroutine context. Cuts pool acquisitions from
     * one per read plus one for the flush down to one per attempt.
     *
     * Only suitable for fast, database-bound units: the connection and the open transaction are held
     * for the whole attempt. Do not use for units that call external services, do heavy CPU work or
     * run parallel reads inside [BaseUnitOfWork.tryPerform] (parallel reads would serialise on the
     * single connection).
     */
    FULL_UOW,
}
