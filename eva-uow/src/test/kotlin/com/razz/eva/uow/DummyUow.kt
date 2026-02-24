package com.razz.eva.uow

import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable

internal abstract class DummyUow(
    executionContext: ExecutionContext,
) : UnitOfWork<TestPrincipal, DummyUow.Params, String>(executionContext) {
    @Serializable
    object Params : UowParams<Params> {
        override fun serialization() = serializer()
    }
}
