package com.razz.eva.domain

import com.razz.eva.domain.EntityState.DirtyState
import com.razz.eva.domain.EntityState.DirtyState.Companion.dirtyState
import com.razz.eva.domain.EntityState.NewState
import com.razz.eva.domain.EntityState.NewState.Companion.newState
import com.razz.eva.domain.EntityState.PersistentState.Companion.persistentState
import com.razz.eva.domain.TestModelEvent.TestModelCreated
import com.razz.eva.domain.TestModelEvent.TestModelEvent1
import com.razz.eva.domain.TestModelEvent.TestModelEvent2
import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
import com.razz.eva.domain.Version.Companion.V0
import com.razz.eva.domain.Version.Companion.V1
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.kotest.matchers.types.beInstanceOf
import io.mockk.coEvery
import io.mockk.mockk

class EntityStateSpec : BehaviorSpec({

    Given("New entity state") {
        val createdEvent = TestModelCreated(randomTestModelId())
        val newState = newState(
            createdEvent = createdEvent
        )

        When("Get version") {
            val version = newState.version()

            Then("Version is zero") {
                version shouldBe V0
            }
        }

        And("Event drive") {
            val eventDrive = mockk<ModelEventDrive<ModelEvent<TestModelId>>>()
            val eventDriveWithCreatedEvent = mockk<ModelEventDrive<ModelEvent<TestModelId>>>()
            coEvery { eventDrive.with(listOf(createdEvent)) } returns eventDriveWithCreatedEvent

            When("Write state events on drive") {
                val updatedDrive = newState.writeEvents(eventDrive)

                Then("Only created event should be written") {
                    updatedDrive shouldBe eventDriveWithCreatedEvent
                }
            }
        }

        And("New events") {
            val event1 = TestModelEvent1(randomTestModelId())
            val event2 = TestModelEvent1(randomTestModelId())

            When("Raise events") {
                val dirtyState = newState.raiseEvent(event1, event2)

                And("Version is zero") {
                    dirtyState.version() shouldBe V0
                }
                And("Entity state should be new") {
                    dirtyState shouldBe beInstanceOf<NewState<TestModelId, TestModelEvent>>()
                }
            }
        }
    }

    Given("Dirty entity state") {
        val modelId = randomTestModelId()
        val firstTestModelEvent = TestModelEvent1(modelId)
        val secondTestModelEvent = TestModelEvent2(modelId)
        val dirtyState = dirtyState(
            version = V1,
            events = listOf(firstTestModelEvent, secondTestModelEvent),
            proto = null,
        )
        And("Event drive") {
            val eventDrive = mockk<ModelEventDrive<TestModelEvent>>()
            val eventDriveWithTwoTestEvents = mockk<ModelEventDrive<TestModelEvent>>()
            coEvery {
                eventDrive.with(
                    listOf(
                        firstTestModelEvent,
                        secondTestModelEvent
                    )
                )
            } returns eventDriveWithTwoTestEvents

            When("Write state events on drive") {
                val updatedDrive = dirtyState.writeEvents(eventDrive)

                Then("Two test events are written") {
                    updatedDrive shouldBe eventDriveWithTwoTestEvents
                }
            }
        }
    }

    Given("Persistent entity state") {
        val persistentState = persistentState<TestModelId, TestModelEvent>(
            version = V1,
            proto = null,
        )

        When("Get version") {
            val version = persistentState.version()

            Then("Updated date and created date are set correctly") {
                version shouldBe V1
            }
        }

        And("New events") {
            val event1 = TestModelEvent1(randomTestModelId())
            val event2 = TestModelEvent1(randomTestModelId())

            When("Raise events") {
                val dirtyState = persistentState.raiseEvent(event1, event2)

                And("Version is one") {
                    dirtyState.version() shouldBe V1
                }
                And("Entity state is dirty") {
                    dirtyState shouldBe beInstanceOf<DirtyState<TestModelId, TestModelEvent>>()
                }
            }
        }
    }

    Given("Invalid persistent state version parameter") {
        val version = V0

        When("Create persistent state") {
            val action = {
                persistentState<TestModelId, TestModelEvent>(version, null)
            }

            Then("Exception is thrown") {
                val e = shouldThrow<IllegalStateException>(action)
                e.message shouldBeEqualIgnoringCase "version should be greater then 0, and events should not occurred"
            }
        }
    }
})
