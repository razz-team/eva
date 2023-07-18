package com.razz.eva.repository

import com.razz.eva.IdempotencyKey
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.events.UowEvent
import com.razz.eva.events.UowEvent.UowName
import com.razz.eva.events.UowEvent.ModelEventId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant.now
import java.util.UUID
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

    object FakePrincipal : Principal<UUID> {
        override val id = Principal.Id(randomUUID())

        override val name = Principal.Name("FakePrincipal")
    }

    val uowEvent = UowEvent(
        id = UowEvent.Id.random(),
        uowName = UowName("Fake"),
        principal = FakePrincipal,
        modelEvents = mapOf(FakeModelEvent.eventId to FakeModelEvent),
        idempotencyKey = IdempotencyKey.random(),
        params = buildJsonObject {
            put("id", FakeId.id.toString())
        }.toString(),
        occurredAt = now()
    )
}
