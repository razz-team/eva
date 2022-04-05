package com.razz.eva.uow

import com.razz.eva.uow.UnitOfWork.Configuration.Companion.withAllowedEmptyChanges
import com.razz.eva.uow.params.UowParams
import kotlinx.serialization.Serializable
import java.time.Clock

class NoChangesConstructorConfigUow(
    clock: Clock
) : UnitOfWork<TestPrincipal, NoChangesConstructorConfigUow.Params, Unit>(clock, withAllowedEmptyChanges()) {

    @Serializable
    object Params : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: TestPrincipal, params: Params): ChangesWithResult<Unit> {
        return changes {}
    }
}
