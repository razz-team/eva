package com.razz.eva.tracing

import io.opentelemetry.api.common.AttributeKey

object OtelAttributes {
    const val SPAN_PERSIST = "persist"
    const val SPAN_PERFORM = "perform"
    const val UOW_OPERATION = "uow.operation"
    const val UOW_NAME = "uow.name"
    val MODEL_ID = AttributeKey.stringArrayKey("model.id")
    val PRE_MERGE_HEAD_MODEL_ID = AttributeKey.stringArrayKey("pre.merge.head.model.id")
    val PRE_MERGE_TAIL_MODEL_ID = AttributeKey.stringArrayKey("pre.merge.tail.model.id")
}