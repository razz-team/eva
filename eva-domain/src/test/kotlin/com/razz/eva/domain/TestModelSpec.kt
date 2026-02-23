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

        When("Get model events for new model") {
            val events = model.modelEvents()

            Then("Only created event should be present") {
                events shouldBe listOf(TestModelCreated(model.id()))
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

        When("Get model events for sequential invocation") {
            val events = model
                .changeParam1("test")
                .changeParam2(999_00L)
                .modelEvents()

            Then("Two events should be present in invocation order") {
                events shouldBe listOf(
                    TestModelEvent1(model.id()),
                    TestModelEvent2(model.id()),
                )
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

        When("Get model events for random invocation") {
            val events = model
                .changeParam1("test1")
                .changeParam2(6666_00L)
                .changeParam1("test2")
                .changeParam2(9999_00L)
                .modelEvents()

            Then("Four events should be present in invocation order") {
                events shouldBe listOf(
                    TestModelEvent1(model.id()),
                    TestModelEvent2(model.id()),
                    TestModelEvent1(model.id()),
                    TestModelEvent2(model.id()),
                )
            }
        }
    }
})
