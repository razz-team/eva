package com.razz.eva.uow

import com.razz.eva.domain.TestModelEvent
import com.razz.eva.domain.TestModelEvent.TestModelCreated
import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

internal class ModelEventDriveSpec : BehaviorSpec({

    Given("Model event") {
        val firstModelEvent = TestModelCreated(randomTestModelId())
        val secondModelEvent = TestModelCreated(randomTestModelId())
        And("Empty model event drive") {
            val eventDrive = ModelEventDrive<TestModelEvent>()

            When("Write event on the drive") {
                val updatedDrive = eventDrive.with(listOf(firstModelEvent, secondModelEvent))

                Then("Event drive contains written events") {
                    updatedDrive shouldBe ModelEventDrive(listOf(firstModelEvent, secondModelEvent))
                }
            }
        }

        And("Not empty event drive") {
            val eventDrive = ModelEventDrive<TestModelEvent>(listOf(firstModelEvent, secondModelEvent))

            When("Get events from the drive") {
                val events = eventDrive.events()

                Then("Events are the same") {
                    events shouldBe listOf(firstModelEvent, secondModelEvent)
                }
            }
        }
    }
})
