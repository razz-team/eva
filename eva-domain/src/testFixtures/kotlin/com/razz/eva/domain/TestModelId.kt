package com.razz.eva.domain

import java.util.*

data class TestModelId(override val id: UUID) : ModelId<UUID> {

    companion object {
        fun randomTestModelId(): TestModelId = TestModelId(UUID.randomUUID())
    }
}
