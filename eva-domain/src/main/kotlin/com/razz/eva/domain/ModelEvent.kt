package com.razz.eva.domain

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface ModelEvent<ID : ModelId<out Comparable<*>>> {
    val modelId: ID
    val modelName: String

    fun eventName(): String {
        return this::class.java.simpleName
    }

    fun integrationEvent(): JsonObject = buildJsonObject {}
}

interface ModelCreatedEvent<ID : ModelId<out Comparable<*>>> : ModelEvent<ID>

interface ModelWithPrincipalEvent<ID : ModelId<out Comparable<*>>> : ModelEvent<ID>

fun ModelEvent<*>.payload(principal: Principal<*>): JsonObject {
    return when (this) {
        is ModelWithPrincipalEvent -> {
            val principalPayload = buildJsonObject {
                put("principalId", principal.id.toString())
                put("principalName", principal.name.toString())
            }
            return JsonObject(principalPayload + integrationEvent())
        }
        else -> {
            integrationEvent()
        }
    }
}
