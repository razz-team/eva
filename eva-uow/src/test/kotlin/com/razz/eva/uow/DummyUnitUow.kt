package com.razz.eva.uow

import kotlinx.serialization.Serializable

internal abstract class DummyUnitUow(
    executionContext: ExecutionContext
) : UnitOfWork<TestPrincipal, DummyUnitUow.Params, Unit>(executionContext) {
    @Serializable
    object Params : UowParams<Params> {
        override fun serialization() = serializer()
    }
}
