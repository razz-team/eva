@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.razz.eva.test.uow

import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.UowParams
import io.mockk.CapturingSlot
import io.mockk.MockK
import io.mockk.MockKDsl
import java.util.concurrent.CopyOnWriteArrayList

inline fun <reified T : UowParams<T>> paramsSlot(): CapturingSlot<InstantiationContext.() -> T> {
    return MockK.useImpl {
        MockKDsl.internalSlot<InstantiationContext.() -> T>()
    }
}

fun <T : UowParams<T>> CapturingSlot<InstantiationContext.() -> T>.captured(): T {
    return (captured)(InstantiationContext(0))
}

inline fun <reified T : UowParams<T>> concurrentSlot() = CopyOnWriteArrayList<InstantiationContext.() -> T>()

val <T : UowParams<T>> CopyOnWriteArrayList<InstantiationContext.() -> T>.captured: T get(): T {
    return (first())(InstantiationContext(0))
}

infix fun <T : UowParams<T>> CopyOnWriteArrayList<InstantiationContext.() -> T>.shouldCapture(matcher: T.() -> Unit) {
    this.any { captured ->
        matcher((captured)(InstantiationContext(0)))
        true
    }
}
