package com.razz.eva.domain

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

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
