package com.razz.eva.tracing

import io.opentracing.Span
import kotlin.coroutines.CoroutineContext

class ActiveSpanElement(
    val span: Span
) : CoroutineContext.Element {

    companion object Key : CoroutineContext.Key<ActiveSpanElement>
    override val key: CoroutineContext.Key<ActiveSpanElement>
        get() = ActiveSpanElement
}
