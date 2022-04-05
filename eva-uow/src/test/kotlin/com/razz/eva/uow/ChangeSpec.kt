package com.razz.eva.uow

import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
import com.razz.eva.domain.Version.Companion.V1
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ChangeSpec : BehaviorSpec({

    Given("Model in PersistentState") {
        val persistedModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        When("Principal tries to UPDATE persisted model via Change") {
            val attempt = { Update(persistedModel, emptyList()) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Can't update non-dirty model"
            }
        }

        When("Principal tries to ADD persisted model via Change") {
            val attempt = { Add(persistedModel, emptyList()) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Can't add non-new model"
            }
        }
    }

    Given("Model in DirtyState") {
        val dirtyModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
            .activate()

        When("Principal tries to ADD dirty model via Change") {
            val attempt = { Add(dirtyModel, emptyList()) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Can't add non-new model"
            }
        }
    }
})
