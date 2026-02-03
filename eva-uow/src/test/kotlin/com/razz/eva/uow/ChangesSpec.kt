package com.razz.eva.uow

import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.RationAllocation
import com.razz.eva.domain.Tag
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
import com.razz.eva.uow.ModelParam.Factory.modelParam
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

        When("Principal calling withUpdated for new model wrapped via ModelParam") {
            // Use a separate model to avoid mutating newModel which is shared across tests
            val separateNewModel = createdTestModel("separate", 999)
            val separateNewModelEvent = TestModelCreated(separateNewModel.id())

            // Simulate ModelParam wrapping: model is wrapped in SnapshotState
            val modelParam = InstantiationContext(0).modelParam(separateNewModel) { error("not used") }
            val wrappedModel = modelParam.model()
            // Modify the wrapped model to make it dirty from user perspective
            val modifiedModel = wrappedModel.changeParam1("modified")

            val changes = ChangesAccumulator()
                .withUpdatedModel(modifiedModel)
                .withResult("snapshot wrapped")

            Then("Changes should be AddModel because underlying state is New") {
                val modelChange = changes.modelChangesToPersist.single()
                // Should be AddModel, not UpdateModel, because framework perspective sees NewState
                modelChange shouldBe AddModel(
                    modifiedModel,
                    listOf(separateNewModelEvent, TestModelEvent1(separateNewModel.id())),
                )
                changes.result shouldBe "snapshot wrapped"
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

        And("Initial changes with entities") {
            val changes0 = ChangesAccumulator().withAddedEntity(tag1)

            When("New changes with entities merged into initial changes") {
                val changes1 = ChangesAccumulator()
                    .withAddedEntity(tag2)
                    .withDeletedEntity(tag1)
                    .withResult("merged")

                val merged = changes0.merge(changes1).withResult("final")

                Then("Entity changes are concatenated") {
                    merged.entityChangesToPersist shouldBe listOf(
                        AddEntity(tag1),
                        AddEntity(tag2),
                        DeleteEntity(tag1),
                    )
                    merged.result shouldBe "final"
                }
            }

            When("Same entity added in both changes and merged") {
                val changes1 = ChangesAccumulator()
                    .withAddedEntity(tag1)
                    .withResult("duplicate")

                val merged = changes0.merge(changes1).withResult("final")

                Then("Duplicate entities are preserved (no deduplication)") {
                    merged.entityChangesToPersist shouldBe listOf(
                        AddEntity(tag1),
                        AddEntity(tag1),
                    )
                    merged.result shouldBe "final"
                }
            }
        }

        And("Initial changes with models and entities") {
            val model = createdTestModel("test", 100)
            val modelEvent = TestModelCreated(model.id())
            val changes0 = ChangesAccumulator()
                .withAddedModel(model)
                .withAddedEntity(tag1)

            When("New changes with models and entities merged") {
                val updatedModel = model.changeParam1("updated")
                val updateEvent = TestModelEvent1(model.id())
                val changes1 = ChangesAccumulator()
                    .withUpdatedModel(updatedModel)
                    .withAddedEntity(tag2)
                    .withResult("merged")

                val merged = changes0.merge(changes1).withResult("final")

                Then("Model changes are merged and entity changes are concatenated") {
                    merged.modelChangesToPersist shouldBe listOf(
                        AddModel(updatedModel, listOf(modelEvent, updateEvent)),
                    )
                    merged.entityChangesToPersist shouldBe listOf(
                        AddEntity(tag1),
                        AddEntity(tag2),
                    )
                    merged.result shouldBe "final"
                }
            }
        }
    }
})
