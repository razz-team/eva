package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
import com.razz.eva.domain.Version.Companion.V1
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ModelChangeSpec : BehaviorSpec({

    val persisting = object : ModelPersisting {
        override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> add(model: M) = Unit
        override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> update(model: M) = Unit
    }

    Given("Model in PersistentState") {
        val persistedModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        When("Principal tries to UPDATE persisted model via ModelChange") {
            val change = UpdateModel(persistedModel, listOf())
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register unchanged " +
                    "model [${persistedModel.id().stringValue()}] as changed"
            }
        }

        When("Principal tries to ADD persisted model via ModelChange") {
            val change = AddModel(persistedModel, listOf())
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register unchanged " +
                    "model [${persistedModel.id().stringValue()}] as new"
            }
        }
    }

    Given("Model in DirtyState") {
        val dirtyModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
            .changeParam1("mlg")

        When("Principal tries to ADD dirty model via ModelChange") {
            val change = AddModel(dirtyModel, listOf())
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register changed " +
                    "model [${dirtyModel.id().stringValue()}] as new"
            }
        }

        When("Principal tries mark dirty model as not changed via ModelChange") {
            val change = NoopModel(dirtyModel)
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register changed " +
                    "model [${dirtyModel.id().stringValue()}] as unchanged"
            }
        }
    }

    Given("Model in NewState") {
        val newModel = createdTestModel("noscope", 360).changeParam1("mlg")

        When("Principal tries to UPDATE new model via ModelChange") {
            val change = UpdateModel(newModel, listOf())
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register new " +
                    "model [${newModel.id().stringValue()}] as changed"
            }
        }

        When("Principal tries mark new model as not changed via ModelChange") {
            val change = NoopModel(newModel)
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register new " +
                    "model [${newModel.id().stringValue()}] as unchanged"
            }
        }
    }
})
