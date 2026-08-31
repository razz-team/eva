package com.razz.eva.uow

import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable

internal abstract class DummyRegisteringUow<T : Any>(
    executionContext: ExecutionContext,
) : RegisteringUnitOfWork<TestPrincipal, DummyRegisteringUow.Params, T>(executionContext) {
    @Serializable
    object Params : UowParams<Params> {
        override fun serialization() = serializer()
    }
}
