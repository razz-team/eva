package com.razz.eva.domain

import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.TestModelEvent.TestModelCreated
import com.razz.eva.domain.TestModelEvent.TestModelEvent1
import com.razz.eva.domain.TestModelEvent.TestModelEvent2
import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
import com.razz.eva.domain.Version.Companion.V1
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk

class TestModelSpec : BehaviorSpec({

    Given("Model instantiated as new state entity") {
        val model = createdTestModel("initial", 100_00L)

        When("Principal invokes change single param method") {
            val updatedModel = model
                .changeParam1("test")

            Then("Original model should not be changed") {
                model.param1 shouldBe "initial"
                model.param2 shouldBe 100_00L
            }
            And("Updated model first param should be changed") {
                updatedModel.param1 shouldBe "test"
            }
            And("Updated model second param should be same") {
                updatedModel.param2 shouldBe 100_00L
            }
        }

        And("Event drive") {
            val eventDrive = mockk<ModelEventDrive<TestModelEvent>>()
            val eventDriveWithCreatedEvent = mockk<ModelEventDrive<TestModelEvent>>()
            coEvery { eventDrive.with(listOf(TestModelCreated(model.id()))) } returns eventDriveWithCreatedEvent

            When("Write state events on drive") {
                val updatedDrive = model.writeEvents(eventDrive)

                Then("Only created event should be written") {
                    updatedDrive shouldBe eventDriveWithCreatedEvent
                }
            }
        }
    }

    Given("Model instantiated as persistent state entity") {
        val model = existingCreatedTestModel(
            id = randomTestModelId(),
            param1 = "initial",
            param2 = 100_00L,
            version = V1,
        )

        When("Principal invokes change different param methods sequentially") {
            val updatedModel = model
                .changeParam1("test")
                .changeParam2(999_00L)

            Then("Original model should not be changed") {
                model.param1 shouldBe "initial"
                model.param2 shouldBe 100_00L
            }
            And("Updated model first param should be changed") {
                updatedModel.param1 shouldBe "test"
            }
            And("Updated model second param should be changed") {
                updatedModel.param2 shouldBe 999_00L
            }
        }

        And("Event drive for sequential invocation") {
            val eventDrive = mockk<ModelEventDrive<TestModelEvent>>()
            val eventDriveWithTwoSequentialEvents = mockk<ModelEventDrive<TestModelEvent>>()
            coEvery {
                eventDrive.with(
                    listOf(
                        TestModelEvent1(model.id()),
                        TestModelEvent2(model.id()),
                    ),
                )
            } returns eventDriveWithTwoSequentialEvents

            When("Principal invokes change different param methods sequentially and write events on drive") {
                val updatedDrive = model
                    .changeParam1("test")
                    .changeParam2(999_00L)
                    .writeEvents(eventDrive)

                Then("Two events should be written in invocation order") {
                    updatedDrive shouldBe eventDriveWithTwoSequentialEvents
                }
            }
        }

        When("Principal invokes change same params method randomly") {
            val updatedModel = model
                .changeParam1("test1")
                .changeParam2(6666_00L)
                .changeParam1("test2")
                .changeParam2(9999_00L)

            Then("Original model should not be changed") {
                model.param1 shouldBe "initial"
                model.param2 shouldBe 100_00L
            }
            And("Updated model first param should be changed") {
                updatedModel.param1 shouldBe "test2"
            }
            And("Updated model second param should be changed") {
                updatedModel.param2 shouldBe 9999_00L
            }
        }

        And("Event drive for random invocation") {
            val eventDrive = mockk<ModelEventDrive<TestModelEvent>>()
            val eventDriveWithFourSequentialEvents = mockk<ModelEventDrive<TestModelEvent>>()
            coEvery {
                eventDrive.with(
                    listOf(
                        TestModelEvent1(model.id()),
                        TestModelEvent2(model.id()),
                        TestModelEvent1(model.id()),
                        TestModelEvent2(model.id()),
                    ),
                )
            } returns eventDriveWithFourSequentialEvents

            When("Principal invokes change same params method randomly and write events on drive") {
                val updatedDrive = model
                    .changeParam1("test1")
                    .changeParam2(6666_00L)
                    .changeParam1("test2")
                    .changeParam2(9999_00L)
                    .writeEvents(eventDrive)

                Then("Two events should be written in invocation order") {
                    updatedDrive shouldBe eventDriveWithFourSequentialEvents
                }
            }
        }
    }
})
