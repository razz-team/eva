package com.razz.eva.repository

import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.uow.Principal
import com.razz.eva.uow.UowEvent
import com.razz.eva.uow.UowEvent.ModelEventId
import com.razz.eva.uow.UowEvent.UowName
import com.razz.eva.uow.params.UowParams
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant.now
import java.util.*
import java.util.UUID.randomUUID

internal object Fake {

    object FakeModelEvent : ModelEvent<FakeId> {

        val eventId = ModelEventId(randomUUID())

        override val modelId = FakeId

        override val modelName = "FakeModel"

        override fun integrationEvent() = buildJsonObject {
            put("id", FakeId.id.toString())
        }
    }

    object FakeId : ModelId<UUID> {
        override val id: UUID = randomUUID()
    }

    @Serializable
    object FakeParams : UowParams<FakeParams> {

        val id = FakeId

        override fun serialization() = serializer()
    }

    object FakePrincipal : Principal<UUID> {
        override val id = Principal.Id(randomUUID())

        override val name = Principal.Name("FakePrincipal")
    }

    val uowEvent = UowEvent(
        id = UowEvent.Id.random(),
        uowName = UowName("Fake"),
        principal = FakePrincipal,
        modelEvents = mapOf(FakeModelEvent.eventId to FakeModelEvent),
        params = FakeParams,
        occurredAt = now()
    )
}
