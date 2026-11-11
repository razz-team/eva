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

// AddModel and UpdateModel check nothing when they persist: every path that builds one picks its kind from
// the model's state (ChangesSpec pins the refusals there). A NoopModel claim still checks, because
// stubChanges claims new and changed models too.
class ModelChangeSpec : BehaviorSpec({

    val persisting = object : ModelPersisting {
        override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> add(model: M) = Unit
        override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> update(model: M) = Unit
    }

    Given("Model in DirtyState") {
        val dirtyModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
            .changeParam1("mlg")

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
