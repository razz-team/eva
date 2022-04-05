package com.razz.eva.domain

import com.razz.eva.domain.TestModel.CreatedTestModel
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.spyk
import io.mockk.verify

class TestModelChangeIfPresentSpec : BehaviorSpec({

    Given("Model instantiated as new state entity") {
        val model = spyk(
            createdTestModel("initial", 100_00L)
        )

        When("Principal invokes change if present param method") {
            val updatedModel = model
                .changeIfPresent("test", CreatedTestModel::changeParam1)
                .changeIfPresent(null, CreatedTestModel::changeParam2)

            Then("Original model should not be changed") {
                model.param1 shouldBe "initial"
                model.param2 shouldBe 100_00L
            }

            And("Original model first param method should be called") {
                verify(exactly = 1) { model.changeParam1("test") }
            }
            And("Updated model first param should be changed") {
                updatedModel.param1 shouldBe "test"
            }
            And("Updated model second param should be same") {
                updatedModel.param2 shouldBe 100_00L
            }
        }
    }
})
