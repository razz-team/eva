package com.razz.eva.domain

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed class TestModelEvent(
    override val modelId: TestModelId
) : ModelEvent<TestModelId> {

    override val modelName = Shakshouka::class.simpleName!!

    data class TestModelStatusChanged(
        val testModelId: TestModelId,
        val oldStatus: TestModelStatus,
        val newStatus: TestModelStatus
    ) : TestModelEvent(testModelId)

    data class TestModelEvent1(
        val testModelId: TestModelId
    ) : TestModelEvent(testModelId)

    data class TestModelEvent2(
        val testModelId: TestModelId
    ) : TestModelEvent(testModelId)

    data class TestModelEventWithPrincipal(
        val testModelId: TestModelId
    ) : TestModelEvent(testModelId), ModelWithPrincipalEvent<TestModelId>

    data class TestModelEventWithOverridePrincipal(
        val testModelId: TestModelId,
        val principal: Principal<*>
    ) : TestModelEvent(testModelId), ModelWithPrincipalEvent<TestModelId> {
        override fun integrationEvent() = buildJsonObject {
            put("principalId", principal.id.toString())
        }
    }

    data class TestModelCreated(
        val testModelId: TestModelId
    ) : TestModelEvent(testModelId), ModelCreatedEvent<TestModelId>
}
