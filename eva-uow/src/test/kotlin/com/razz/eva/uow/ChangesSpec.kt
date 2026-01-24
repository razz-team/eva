package com.razz.eva.uow

import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.TestModelEvent.TestModelCreated
import com.razz.eva.domain.TestModelEvent.TestModelEvent1
import com.razz.eva.domain.TestModelEvent.TestModelEvent2
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
                ChangesAccumulator().withResult("nelson mandela")
            }

            Then("IllegalArgumentException thrown") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldBe "No changes to persist"
            }
        }

        When("Principal calling withAdded and then withResult") {
            val changes = ChangesAccumulator()
                .withAddedModel(newModel)
                .withResult("patrice lumumba")

            Then("Changes matching added and result produced") {
                changes.modelChangesToPersist shouldBe listOf(AddModel(newModel, listOf(newModelEvent)))
                changes.result shouldBe "patrice lumumba"
            }
        }

        When("Principal calling withAdded twice for the same model") {
            val attempt = {
                ChangesAccumulator()
                    .withAddedModel(newModel)
                    .withAddedModel(newModel)
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldBe "Change for a given model [${newModel.id()}] was already registered"
            }
        }

        When("Principal calling withAdded and then withUpdated for the same model") {
            val attempt = {
                ChangesAccumulator()
                    .withAddedModel(newModel)
                    // I doubt this could ever happen in regular uow
                    // since model has to be round tripped through db
                    // or hacked in some other way to appear in dirty state
                    // but this container must preserve correct behavior nevertheless
                    .withUpdatedModel(existingCreatedTestModel(newModel.id(), "noscope", 360, V1).activate())
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldBe "Change for a given model [${newModel.id()}] was already registered"
            }
        }

        When("Principal calling withAdded for not new model") {
            val changes = ChangesAccumulator()
                .withAddedModel(unchangedModel)
                .withResult("george floyd")

            Then("Changes matching added and result produced") {
                changes.modelChangesToPersist shouldBe listOf(AddModel(unchangedModel, listOf()))
                changes.result shouldBe "george floyd"
            }
        }

        When("Principal calling withAdded for dirty model") {
            val changes = ChangesAccumulator()
                .withAddedModel(dirtyModel)
                .withResult("nahel merzouk")

            Then("Changes matching added and result produced") {
                changes.modelChangesToPersist shouldBe listOf(AddModel(dirtyModel, listOf(dirtyModelEvent)))
                changes.result shouldBe "nahel merzouk"
            }
        }

        When("Principal calling withUpdated and then withResult") {
            val changes = ChangesAccumulator()
                .withUpdatedModel(dirtyModel)
                .withResult("angela davis")

            Then("Changes matching updated and result produced") {
                changes.modelChangesToPersist shouldBe listOf(UpdateModel(dirtyModel, listOf(dirtyModelEvent)))
                changes.result shouldBe "angela davis"
            }
        }

        When("Principal calling withUpdated for not updated model") {
            val changes = ChangesAccumulator()
                .withUpdatedModel(unchangedModel)
                .withResult("george floyd")

            Then("Changes matching updated and result produced") {
                changes.modelChangesToPersist shouldBe listOf(UpdateModel(unchangedModel, listOf()))
                changes.result shouldBe "george floyd"
            }
        }

        When("Principal calling withUpdated for new model") {
            val changes = ChangesAccumulator()
                .withUpdatedModel(newModel)
                .withResult("nahel merzouk")

            Then("Changes matching updated and result produced") {
                changes.modelChangesToPersist shouldBe listOf(UpdateModel(newModel, listOf(newModelEvent)))
                changes.result shouldBe "nahel merzouk"
            }
        }

        When("Principal calling withUpdated twice for the same model") {
            val attempt = {
                ChangesAccumulator()
                    .withUpdatedModel(dirtyModel)
                    .withUpdatedModel(dirtyModel)
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldBe "Change for a given model [${dirtyModel.id()}] was already registered"
            }
        }

        When("Principal calling withUnchanged for the unchanged model and then withResult") {
            val changes = ChangesAccumulator()
                .withUnchangedModel(unchangedModel)
                .withResult("robert mugabe")

            Then("NoopModel and result produced") {
                changes.modelChangesToPersist shouldBe listOf(NoopModel(unchangedModel))
                changes.result shouldBe "robert mugabe"
            }
        }

        When("Principal calling withUnchanged twice for the same model") {
            val attempt = {
                ChangesAccumulator()
                    .withUnchangedModel(unchangedModel)
                    .withUnchangedModel(unchangedModel)
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldBe "Change for a given model [${unchangedModel.id()}] was already registered"
            }
        }

        When("Principal calling withUnchanged and then withUpdated for the same model") {
            val attempt = {
                ChangesAccumulator()
                    .withUnchangedModel(unchangedModel)
                    .withUpdatedModel(unchangedModel.activate())
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldBe "Change for a given model [${unchangedModel.id()}] was already registered"
            }
        }

        When("Principal calling withUnchanged and then withUpdated for not updated model") {
            val attempt = {
                ChangesAccumulator()
                    .withUnchangedModel(unchangedModel)
                    .withUpdatedModel(unchangedModel)
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldBe
                    "Change for a given model [${unchangedModel.id()}] was already registered"
            }
        }

        When("Principal calling withUnchanged for the dirty model") {
            val changes = ChangesAccumulator()
                .withUnchangedModel(dirtyModel)
                .withResult("robert mugabe")

            Then("NoopModel and result produced") {
                changes.modelChangesToPersist shouldBe listOf(NoopModel(dirtyModel))
                changes.result shouldBe "robert mugabe"
            }
        }

        When("Principal calling withUnchanged for the new model") {
            val changes = ChangesAccumulator()
                .withUnchangedModel(newModel)
                .withResult("angela davis")

            Then("NoopModel and result produced") {
                changes.modelChangesToPersist shouldBe listOf(NoopModel(newModel))
                changes.result shouldBe "angela davis"
            }
        }

        And("initial changes with some additional models") {
            val changes0 = ChangesAccumulator().withAddedModel(newModel)
            val model1 = existingCreatedTestModel(randomTestModelId(), "name1", 1337, V1)
                .activate()
            val model1Event = TestModelStatusChanged(model1.id(), CREATED, ACTIVE)
            val model2 = existingCreatedTestModel(randomTestModelId(), "name2", 100500, V1)
                .activate()
            val model2Event = TestModelStatusChanged(model2.id(), CREATED, ACTIVE)
            val model3 = existingCreatedTestModel(randomTestModelId(), "name3", 0xBABE, V1)
                .activate()
            val model3Event = TestModelStatusChanged(model3.id(), CREATED, ACTIVE)

            When("derived changes produced by adding models to initial changes") {
                val changes1 = changes0.withUpdatedModel(model1).withResult(model2)

                And("initial changes are modified further") {
                    val changes2 = changes0.withUpdatedModel(model3)

                    And("initial changes completed with result") {
                        val finalChanges0 = changes2.withResult(listOf("Gurbanguly", "Berdimuhamedow"))

                        Then("original changes contain only models were added to it directly") {
                            finalChanges0.modelChangesToPersist shouldBe listOf(
                                AddModel(newModel, listOf(newModelEvent)),
                                UpdateModel(model3, listOf(model3Event)),
                            )
                            finalChanges0.result shouldBe listOf("Gurbanguly", "Berdimuhamedow")
                        }
                        And("derived changes contain only models were added to it directly") {
                            changes1.modelChangesToPersist shouldBe listOf(
                                AddModel(newModel, listOf(newModelEvent)),
                                UpdateModel(model1, listOf(model1Event)),
                            )
                            changes1.result shouldBe model2
                        }
                    }
                }
            }

            When("another changes produced") {
                val updatedNewModel = newModel.changeParam1("new name").changeParam2(0xBABE)
                val newModelEvent1 = TestModelEvent1(newModel.id())
                val newModelEvent2 = TestModelEvent2(newModel.id())
                val changes1 = ChangesAccumulator()
                    .withUpdatedModel(updatedNewModel)
                    .withUpdatedModel(model1)
                    .withResult("whatever")

                And("new changes merged into initial changes") {
                    val changes3 = changes0.merge(changes1).withUpdatedModel(model2).withResult("whatever")

                    Then("original changes contain only models were added to it directly") {
                        changes3.modelChangesToPersist shouldBe listOf(
                            AddModel(updatedNewModel, listOf(newModelEvent, newModelEvent1, newModelEvent2)),
                            UpdateModel(model1, listOf(model1Event)),
                            UpdateModel(model2, listOf(model2Event)),
                        )
                    }
                }
            }

            When("incompatible changes produced") {
                val changes1 = ChangesAccumulator()
                    .withAddedModel(newModel)
                    .withResult("whatever")

                And("new changes merged into initial changes") {
                    val attempt = { changes0.merge(changes1).withResult("whatever") }

                    Then("original changes contain only models were added to it directly") {
                        val ex = shouldThrow<IllegalStateException>(attempt)
                        ex.message shouldBe "Failed to merge changes for model [${newModel.id()}]"
                    }
                }
            }
        }
    }
})
