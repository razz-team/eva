package com.razz.eva.uow

import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.TestModelEvent.TestModelCreated
import com.razz.eva.domain.TestModelEvent.TestModelStatusChanged
import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
import com.razz.eva.domain.TestModelStatus.ACTIVE
import com.razz.eva.domain.TestModelStatus.CREATED
import com.razz.eva.domain.Version.Companion.V1
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ChangesSpec : BehaviorSpec({

    Given("An arbitrary model") {
        val newModel = createdTestModel("name", 420)
        val newModelEvent = TestModelCreated(newModel.id())
        val dirtyModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
            .activate()
        val dirtyModelEvent = TestModelStatusChanged(dirtyModel.id(), CREATED, ACTIVE)
        val unchangedModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        When("Principal calling withResult on empty changes") {
            val attempt = {
                ChangesWithoutResult().withResult("nelson mandela")
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldBe "No changes to persist"
            }
        }

        When("Principal calling withResult on empty changes when emptyChangesAllowed flag is true") {
            val changes = ChangesWithoutResult(true)
                .withResult("nelson mandela")

            Then("Changes matching added and result produced") {
                changes.toPersist shouldBe emptyList()
                changes.result shouldBe "nelson mandela"
            }
        }

        When("Principal calling withAdded and then withResult") {
            val changes = ChangesWithoutResult()
                .withAdded(newModel).withResult("patrice lumumba")

            Then("Changes matching added and result produced") {
                changes.toPersist shouldBe listOf(Add(newModel, listOf(newModelEvent)))
                changes.result shouldBe "patrice lumumba"
            }
        }

        When("Principal calling withAdded twice with the same model") {
            val attempt = {
                ChangesWithoutResult()
                    .withAdded(newModel)
                    .withAdded(newModel)
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldBe "Change for a given model [${newModel.id()}] was already registered"
            }
        }

        When("Principal calling withAdded and then withUpdated with the same model") {
            val attempt = {
                ChangesWithoutResult()
                    .withAdded(newModel)
                    // I doubt this could ever happen in regular uow
                    // since model has to be roundtripped through db
                    // or hacked in some other way to appear in dirty state
                    // but this container must preserve correct behavior nevertheless
                    .withUpdated(existingCreatedTestModel(newModel.id(), "noscope", 360, V1).activate())
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldBe "Change for a given model [${newModel.id()}] was already registered"
            }
        }

        When("Principal calling withUpdated and then withResult") {
            val changes = ChangesWithoutResult()
                .withUpdated(dirtyModel).withResult("angela davis")

            Then("Changes matching updated and result produced") {
                changes.toPersist shouldBe listOf(Update(dirtyModel, listOf(dirtyModelEvent)))
                changes.result shouldBe "angela davis"
            }
        }

        When("Principal calling withUpdated with not updated model") {
            val attempt = {
                ChangesWithoutResult()
                    .withUpdated(unchangedModel)
            }

            Then("IllegalArgumentException thrown") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldBe
                    "Attempted to register unchanged model [${unchangedModel.id()}] but empty changes were disallowed"
            }
        }

        When("Principal calling withUpdated twice with the same model") {
            val attempt = {
                ChangesWithoutResult()
                    .withUpdated(dirtyModel)
                    .withUpdated(dirtyModel)
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldBe "Change for a given model [${dirtyModel.id()}] was already registered"
            }
        }

        When("Principal calling withUnchanged with the unchanged model and then withResult") {
            val changes = ChangesWithoutResult()
                .withUnchanged(unchangedModel)
                .withResult("robert mugabe")

            Then("Noop and result produced") {
                changes.toPersist shouldBe listOf(Noop)
                changes.result shouldBe "robert mugabe"
            }
        }

        When("Principal calling withUnchanged twice with the same model") {
            val attempt = {
                ChangesWithoutResult()
                    .withUnchanged(unchangedModel)
                    .withUnchanged(unchangedModel)
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldBe "Change for a given model [${unchangedModel.id()}] was already registered"
            }
        }

        When("Principal calling withUnchanged and then withUpdated with the same model") {
            val attempt = {
                ChangesWithoutResult()
                    .withUnchanged(unchangedModel)
                    .withUpdated(unchangedModel.activate())
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldBe "Change for a given model [${unchangedModel.id()}] was already registered"
            }
        }

        When("Principal calling withUnchanged and then withUpdated with the same model") {
            val attempt = {
                ChangesWithoutResult()
                    .withUnchanged(unchangedModel)
                    .withUpdated(unchangedModel.activate())
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldBe "Change for a given model [${unchangedModel.id()}] was already registered"
            }
        }

        When("Principal calling withUnchanged and then withUpdated with not updated model") {
            val attempt = {
                ChangesWithoutResult()
                    .withUnchanged(unchangedModel)
                    .withUpdated(unchangedModel)
            }

            Then("IllegalArgumentException thrown") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldBe
                    "Attempted to register unchanged model [${unchangedModel.id()}] but empty changes were disallowed"
            }
        }

        When("Principal calling withUnchanged with the dirty model") {
            val attempt = {
                ChangesWithoutResult()
                    .withUnchanged(dirtyModel)
            }

            Then("IllegalArgumentException thrown") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldBe "Attempted mark dirty model [${dirtyModel.id()}] as unchanged"
            }
        }

        When("Principal calling withUnchanged with the new model") {
            val attempt = {
                ChangesWithoutResult()
                    .withUnchanged(newModel)
            }

            Then("IllegalArgumentException thrown") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldBe "Attempted mark new model [${newModel.id()}] as unchanged"
            }
        }

        And("initial changes with some additional models") {
            val changes0 = ChangesWithoutResult().withAdded(newModel)
            val model1 = existingCreatedTestModel(randomTestModelId(), "name1", 1337, V1)
                .activate()
            val model1Event = TestModelStatusChanged(model1.id(), CREATED, ACTIVE)
            val model2 = existingCreatedTestModel(randomTestModelId(), "name2", 100500, V1)
                .activate()
            val model3 = existingCreatedTestModel(randomTestModelId(), "name3", 0xBABE, V1)
                .activate()
            val model3Event = TestModelStatusChanged(model3.id(), CREATED, ACTIVE)

            When("derived changes produced by adding models to initial changes") {
                val changes1 = changes0.withUpdated(model1).withResult(model2)

                And("initial changes are modified further") {
                    val changes2 = changes0.withUpdated(model3)

                    And("initial changes completed with result") {
                        val finalChanges0 = changes2.withResult(listOf("Gurbanguly", "Berdimuhamedow"))

                        Then("original changes contain only models were added to it directly") {
                            finalChanges0.toPersist shouldBe listOf(
                                Add(newModel, listOf(newModelEvent)),
                                Update(model3, listOf(model3Event))
                            )
                            finalChanges0.result shouldBe listOf("Gurbanguly", "Berdimuhamedow")
                        }
                        And("derived changes contain only models were added to it directly") {
                            changes1.toPersist shouldBe listOf(
                                Add(newModel, listOf(newModelEvent)),
                                Update(model1, listOf(model1Event))
                            )
                            changes1.result shouldBe model2
                        }
                    }
                }
            }
        }
    }
})
