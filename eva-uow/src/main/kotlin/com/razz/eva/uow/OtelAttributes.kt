package com.razz.eva.uow

import io.opentelemetry.api.common.AttributeKey

internal object OtelAttributes {
    const val SPAN_PERSIST = "persist"
    const val SPAN_PERFORM = "perform"
    const val UOW_OPERATION = "uow.operation"
    const val UOW_ID = "uow.id"
    const val UOW_NAME = "uow.name"
    const val PRINCIPAL_ID = "principal.id"
    const val MODEL_NAME = "model.name"
    const val EVENT_NAME = "event.name"
    val MODEL_ID = AttributeKey.stringArrayKey("model.id")
}
