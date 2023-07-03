@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.razz.eva.test.uow

import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.UowParams
import io.mockk.CapturingSlot
import io.mockk.MockK
import io.mockk.MockKDsl

inline fun <reified T : UowParams<T>> paramsSlot(): CapturingSlot<InstantiationContext.() -> T> {
    return MockK.useImpl {
        MockKDsl.internalSlot<InstantiationContext.() -> T>()
    }
}

fun <T : UowParams<T>> CapturingSlot<InstantiationContext.() -> T>.captured(): T {
    return (captured)(InstantiationContext(0))
}
