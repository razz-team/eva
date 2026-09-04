package com.razz.eva.uow.composable

import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable

internal abstract class DummyProvingUow<T : Any>(executionContext: ExecutionContext) :
    ProvingUnitOfWork<TestPrincipal, DummyProvingUow.Params, T>(executionContext) {
    @Serializable
    object Params : UowParams<Params> {
        override fun serialization() = serializer()
    }
}
