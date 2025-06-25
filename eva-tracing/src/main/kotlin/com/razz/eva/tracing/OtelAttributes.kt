package com.razz.eva.tracing

import io.opentelemetry.api.common.AttributeKey

object OtelAttributes {
    const val SPAN_PERSIST = "persist"
    const val SPAN_PERFORM = "perform"
    const val UOW_OPERATION = "uow.operation"
    const val UOW_NAME = "uow.name"
    internal val MODEL_ID = AttributeKey.stringArrayKey("model.id")
}
