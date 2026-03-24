package com.razz.eva.uow

import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable

class UnregisteredUow(
    executionContext: ExecutionContext,
) : UnitOfWork<TestPrincipal, UnregisteredUow.Params, Unit>(executionContext) {

    @Serializable
    data class Params(val unit: Unit = Unit) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: TestPrincipal, params: Params) = noChanges()
}
