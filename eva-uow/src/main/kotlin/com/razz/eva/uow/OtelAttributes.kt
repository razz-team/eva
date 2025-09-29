package com.razz.eva.uow

import io.opentelemetry.api.common.AttributeKey

internal object OtelAttributes {
    const val SPAN_PERSIST = "persist"
    const val SPAN_PERFORM = "perform"
    const val UOW_OPERATION = "uow.operation"
    const val UOW_NAME = "uow.name"
    const val UOW_EXCEPTION_TYPE = "uow.exception.type"
    const val PRINCIPAL_ID = "principal.id"
    val MODEL_ID = AttributeKey.stringArrayKey("model.id")
}
