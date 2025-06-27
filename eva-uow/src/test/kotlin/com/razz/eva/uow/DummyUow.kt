package com.razz.eva.uow

import kotlinx.serialization.Serializable
import java.time.Clock

internal abstract class DummyUow(clock: Clock) : UnitOfWork<TestPrincipal, DummyUow.Params, String>(clock) {
    @Serializable
    object Params : UowParams<Params> {
        override fun serialization() = serializer()
    }
}
