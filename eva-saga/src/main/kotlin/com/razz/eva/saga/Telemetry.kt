package com.razz.eva.saga

import io.opentelemetry.api.common.AttributeKey

internal object Metrics {
    const val OBSERVER_FAILURE = "saga.observer.failure"
    const val RESTART = "saga.restart"
    const val OUTCOME = "saga.outcome"
}

internal object Events {
    const val RESTART = "saga.restart"
}

internal enum class RunOutcome(val value: String) {
    TERMINAL("terminal"),
    MAPPED("mapped"),
    RETHREW("rethrew"),
    GAVE_UP("gave_up"),
}

internal enum class ObserverOutcome(val value: String) {
    TIMED_OUT("timed_out"),
    THREW("threw"),
}

internal object OtelAttributes {
    const val UNKNOWN = "Unknown"
    val SAGA_NAME = AttributeKey.stringKey("saga.name")
    val SAGA_TERMINAL = AttributeKey.stringKey("saga.terminal")
    val SAGA_RUN_ID = AttributeKey.stringKey("saga.run_id")
    val SAGA_PARENT_RUN_ID = AttributeKey.stringKey("saga.parent_run_id")
    val SAGA_ATTEMPT = AttributeKey.longKey("saga.attempt")
    val SAGA_ATTEMPTS = AttributeKey.longKey("saga.attempts")
    val SAGA_OUTCOME = AttributeKey.stringKey("saga.outcome")
    val SAGA_EXCEPTION = AttributeKey.stringKey("saga.exception")
    val OBSERVER_OUTCOME = AttributeKey.stringKey("saga.observer.outcome")
}
