package com.razz.eva.uow

import kotlinx.serialization.Serializable
import java.time.Clock

internal abstract class DummyUnitUow(clock: Clock) : UnitOfWork<TestPrincipal, DummyUnitUow.Params, Unit>(clock) {
    @Serializable
    object Params : UowParams<Params> {
        override fun serialization() = serializer()
    }
}
