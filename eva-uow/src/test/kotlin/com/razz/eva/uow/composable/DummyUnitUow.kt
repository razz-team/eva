package com.razz.eva.uow.composable

import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UowParams
import kotlinx.serialization.Serializable

internal abstract class DummyUnitUow(executionContext: ExecutionContext) :
    UnitOfWork<TestPrincipal, DummyUnitUow.Params, Unit>(executionContext) {
    @Serializable
    object Params : UowParams<Params> {
        override fun serialization() = serializer()
    }
}
