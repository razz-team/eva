package com.razz.eva.domain

import com.razz.eva.domain.EntityState.NewState.Companion.newState
import com.razz.eva.domain.EntityState.PersistentState.Companion.persistentState
import com.razz.eva.domain.TestModelEvent.TestModelCreated
import com.razz.eva.domain.TestModelEvent.TestModelEvent1
import com.razz.eva.domain.TestModelEvent.TestModelEvent2
import com.razz.eva.domain.TestModelEvent.TestModelStatusChanged
import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
import com.razz.eva.domain.TestModelStatus.ACTIVE
import com.razz.eva.domain.TestModelStatus.CREATED
import com.razz.eva.domain.Version.Companion.V1

sealed class TestModel private constructor(
    id: TestModelId,
    param1: String?,
    param2: Long,
    entityState: EntityState<TestModelId, TestModelEvent>
) : Model<TestModelId, TestModelEvent>(id, entityState) {

    init {
        checkNotNull(param1)
        require(param2 >= 0)
    }

    fun status(): TestModelStatus = when (this) {
        is CreatedTestModel -> CREATED
        is ActiveTestModel -> ACTIVE
    }

    class CreatedTestModel internal constructor(
        id: TestModelId,
        val param1: String?,
        val param2: Long,
        entityState: EntityState<TestModelId, TestModelEvent>
    ) : TestModel(id, param1, param2, entityState) {

        fun activate(): ActiveTestModel {
            checkNotNull(param1)
            return ActiveTestModel(
                id = id(),
                param1 = param1,
                param2 = param2,
                entityState = entityState().raiseEvent(
                    TestModelStatusChanged(
                        testModelId = id(),
                        newStatus = ACTIVE,
                        oldStatus = status()
                    )
                )
            )
        }

        fun changeParam1(param1: String): CreatedTestModel {
            return copy(
                param1 = param1,
                raisedEvent = TestModelEvent1(id())
            )
        }

        fun changeParam2(param2: Long): CreatedTestModel {
            return copy(
                param2 = param2,
                raisedEvent = TestModelEvent2(id())
            )
        }

        private fun copy(
            param1: String? = this.param1,
            param2: Long = this.param2,
            raisedEvent: TestModelEvent
        ): CreatedTestModel {
            return CreatedTestModel(
                id = id(),
                param1 = param1,
                param2 = param2,
                entityState().raiseEvent(raisedEvent)
            )
        }
    }

    class ActiveTestModel internal constructor(
        id: TestModelId,
        val param1: String,
        val param2: Long,
        entityState: EntityState<TestModelId, TestModelEvent>
    ) : TestModel(id, param1, param2, entityState)

    companion object Factory {
        fun createdTestModel(param1: String?, param2: Long): CreatedTestModel {
            val testModelId = randomTestModelId()
            return CreatedTestModel(
                id = testModelId,
                param1 = param1,
                param2 = param2,
                newState(TestModelCreated(testModelId))
            )
        }

        fun existingCreatedTestModel(
            id: TestModelId = randomTestModelId(),
            param1: String?,
            param2: Long,
            version: Version = V1
        ): CreatedTestModel {
            return CreatedTestModel(
                id = id,
                param1 = param1,
                param2 = param2,
                persistentState(version)
            )
        }
    }
}
