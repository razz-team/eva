package com.razz.eva.uow

import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.RationAllocation
import com.razz.eva.domain.Tag
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
import java.time.LocalDate
import java.util.UUID

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
                exception.message shouldBe "Change for a given " +
                    "model [${newModel.id().stringValue()}] was already registered"
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
                exception.message shouldBe "Change for a given " +
                    "model [${newModel.id().stringValue()}] was already registered"
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
                exception.message shouldBe "Change for a given " +
                    "model [${dirtyModel.id().stringValue()}] was already registered"
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
                exception.message shouldBe "Change for a given " +
                    "model [${unchangedModel.id().stringValue()}] was already registered"
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
                exception.message shouldBe "Change for a given " +
                    "model [${unchangedModel.id().stringValue()}] was already registered"
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
                    "Change for a given model [${unchangedModel.id().stringValue()}] was already registered"
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
        }
    }
    Given("ChangesAccumulator query and replace methods") {
        val model1 = createdTestModel("model1", 100)
        val model1Event = TestModelCreated(model1.id())
        val model2 = existingCreatedTestModel(randomTestModelId(), "model2", 200, V1).activate()
        val model2Event = TestModelStatusChanged(model2.id(), CREATED, ACTIVE)

        When("changeFor is called on empty accumulator") {
            val result = ChangesAccumulator().changeFor(model1.id())

            Then("null is returned") {
                result shouldBe null
            }
        }

        When("changeFor is called for existing model") {
            val acc = ChangesAccumulator().withAddedModel(model1)
            val result = acc.changeFor(model1.id())

            Then("the change is returned") {
                result shouldBe AddModel(model1, listOf(model1Event))
            }
        }

        When("changeFor is called for non-existing model") {
            val acc = ChangesAccumulator().withAddedModel(model1)
            val result = acc.changeFor(model2.id())

            Then("null is returned") {
                result shouldBe null
            }
        }

        When("modelIds is called on empty accumulator") {
            val result = ChangesAccumulator().modelIds()

            Then("empty set is returned") {
                result shouldBe emptySet()
            }
        }

        When("modelIds is called on accumulator with models") {
            val acc = ChangesAccumulator().withAddedModel(model1).withUpdatedModel(model2)
            val result = acc.modelIds()

            Then("set of model ids is returned") {
                result shouldBe setOf(model1.id(), model2.id())
            }
        }

        When("withReplacedModelChange is called for existing model") {
            val acc = ChangesAccumulator().withAddedModel(model1)
            val updatedModel1 = model1.activate()
            val statusChanged = TestModelStatusChanged(model1.id(), CREATED, ACTIVE)
            val replacement = AddModel(updatedModel1, listOf(model1Event, statusChanged))
            val result = acc.withReplacedModelChange(model1.id(), replacement).withResult("replaced")

            Then("the change is replaced") {
                result.modelChangesToPersist shouldBe listOf(replacement)
                result.result shouldBe "replaced"
            }
        }

        When("from is called with Changes") {
            val changes = ChangesAccumulator()
                .withAddedModel(model1)
                .withUpdatedModel(model2)
                .withResult("original")

            val restored = ChangesAccumulator.from(changes).withResult("restored")

            Then("accumulator contains same model changes") {
                restored.modelChangesToPersist shouldBe listOf(
                    AddModel(model1, listOf(model1Event)),
                    UpdateModel(model2, listOf(model2Event)),
                )
                restored.result shouldBe "restored"
            }
        }

        When("from is called with Changes that include entities") {
            val subjectId = UUID.randomUUID()
            val tag = Tag(subjectId, "key", "value")
            val changes = ChangesAccumulator()
                .withAddedModel(model1)
                .withAddedEntity(tag)
                .withResult("with-entities")

            val restored = ChangesAccumulator.from(changes).withResult("restored")

            Then("accumulator contains both model and entity changes") {
                restored.modelChangesToPersist shouldBe listOf(
                    AddModel(model1, listOf(model1Event)),
                )
                restored.entityChangesToPersist shouldBe listOf(AddEntity(tag))
                restored.result shouldBe "restored"
            }
        }
    }

    Given("Entities") {
        val subjectId = UUID.randomUUID()
        val tag1 = Tag(subjectId, "key1", "value1")
        val tag2 = Tag(subjectId, "key2", "value2")
        val employeeId = EmployeeId(UUID.randomUUID())
        val allocation = RationAllocation(employeeId, BUBALEH, LocalDate.now(), 5)

        When("Principal calling withAddedEntity and then withResult") {
            val changes = ChangesAccumulator()
                .withAddedEntity(tag1)
                .withResult("tag added")

            Then("Changes matching added entity and result produced") {
                changes.entityChangesToPersist shouldBe listOf(AddEntity(tag1))
                changes.modelChangesToPersist shouldBe emptyList()
                changes.result shouldBe "tag added"
            }
        }

        When("Principal calling withAddedEntity twice for different entities") {
            val changes = ChangesAccumulator()
                .withAddedEntity(tag1)
                .withAddedEntity(tag2)
                .withResult("tags added")

            Then("Changes contain both entities") {
                changes.entityChangesToPersist shouldBe listOf(AddEntity(tag1), AddEntity(tag2))
                changes.result shouldBe "tags added"
            }
        }

        When("Principal calling withAddedEntity twice for the same entity") {
            val changes = ChangesAccumulator()
                .withAddedEntity(tag1)
                .withAddedEntity(tag1)
                .withResult("duplicate tag")

            Then("Changes contain duplicate entities (no deduplication)") {
                changes.entityChangesToPersist shouldBe listOf(AddEntity(tag1), AddEntity(tag1))
                changes.result shouldBe "duplicate tag"
            }
        }

        When("Principal calling withDeletedEntity and then withResult") {
            val changes = ChangesAccumulator()
                .withDeletedEntity(tag1)
                .withResult("tag deleted")

            Then("Changes matching deleted entity and result produced") {
                changes.entityChangesToPersist shouldBe listOf(DeleteEntity(tag1))
                changes.result shouldBe "tag deleted"
            }
        }

        When("Principal calling withAddedEntity and withDeletedEntity") {
            val changes = ChangesAccumulator()
                .withAddedEntity(tag1)
                .withDeletedEntity(tag2)
                .withResult("mixed entity ops")

            Then("Changes contain both add and delete") {
                changes.entityChangesToPersist shouldBe listOf(AddEntity(tag1), DeleteEntity(tag2))
                changes.result shouldBe "mixed entity ops"
            }
        }

        When("Principal calling withAddedEntity for CreatableEntity (not DeletableEntity)") {
            val changes = ChangesAccumulator()
                .withAddedEntity(allocation)
                .withResult("allocation added")

            Then("Changes matching added allocation and result produced") {
                changes.entityChangesToPersist shouldBe listOf(AddEntity(allocation))
                changes.result shouldBe "allocation added"
            }
        }

        And("A model") {
            val model = createdTestModel("test", 100)
            val modelEvent = TestModelCreated(model.id())

            When("Principal adds both model and entity") {
                val changes = ChangesAccumulator()
                    .withAddedModel(model)
                    .withAddedEntity(tag1)
                    .withResult("mixed changes")

                Then("Changes contain both model and entity changes") {
                    changes.modelChangesToPersist shouldBe listOf(AddModel(model, listOf(modelEvent)))
                    changes.entityChangesToPersist shouldBe listOf(AddEntity(tag1))
                    changes.result shouldBe "mixed changes"
                }
            }
        }
    }
})
