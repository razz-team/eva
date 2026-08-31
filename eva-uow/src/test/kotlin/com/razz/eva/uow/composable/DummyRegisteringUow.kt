package com.razz.eva.uow.composable

import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable

internal abstract class DummyRegisteringUow<T : Any>(executionContext: ExecutionContext) :
    RegisteringUnitOfWork<TestPrincipal, DummyRegisteringUow.Params, T>(executionContext) {
    @Serializable
    object Params : UowParams<Params> {
        override fun serialization() = serializer()
    }
}
