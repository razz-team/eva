package com.razz.eva.domain

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

    data class TestModelCreated(
        val testModelId: TestModelId
    ) : TestModelEvent(testModelId), ModelCreatedEvent<TestModelId>
}
