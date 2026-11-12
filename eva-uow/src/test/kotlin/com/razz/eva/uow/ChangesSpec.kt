@file:OptIn(com.razz.eva.uow.TestDoubleApi::class)

package com.razz.eva.uow

import com.razz.eva.domain.mutating
import com.razz.eva.domain.Aggregate
import com.razz.eva.domain.DepartmentEvent
import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DeptAggregate
import com.razz.eva.domain.DeptAggregate.Companion.newDeptAggregate
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeEvent.EmployeeCreated
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelState
import com.razz.eva.domain.ModelState.NewState.Companion.newState
import com.razz.eva.domain.ModelState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Name
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
import com.razz.eva.domain.addEmployee
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.string.shouldStartWith
import java.time.LocalDate
import java.util.UUID

class ChangesSpec : BehaviorSpec({

    Given("A registration a block cannot make") {
        val newModel = createdTestModel("name", 420)
        val dirtyModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1).activate()
        val unchangedModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
        fun id(model: Model<*, *>) = model.id().stringValue()

        Then("each message says what the block did, what would go wrong and what to write") {
            val added = ChangesAccumulator().apply { adding(newModel) }
            shouldThrow<IllegalStateException> { added.adding(newModel) }
                .message shouldBe "Change for a given model [${id(newModel)}] was already registered. The block " +
                "registered it twice, and the second registration would replace the first and drop its write. " +
                "Register a model once: make every mutation first, then call add or update with the last " +
                "instance. A second update is accepted only for a model the parent or a composed child registered"
            shouldThrow<IllegalArgumentException> { ChangesAccumulator().adding(dirtyModel) }
                .message shouldBe "Attempted to register changed model [${id(dirtyModel)}] as new. It is stored " +
                "already, so inserting it would fail. Register a stored model with update, or with notChanged " +
                "when it did not change"
            shouldThrow<IllegalArgumentException> { ChangesAccumulator().updating(newModel) }
                .message shouldBe "Attempted to register new model [${id(newModel)}] as changed. It is not stored " +
                "yet, so there is nothing to update. Register a new model with add. If the block added it already, " +
                "make every mutation first and add the last instance"
            shouldThrow<IllegalArgumentException> { ChangesAccumulator().updating(unchangedModel) }
                .message shouldBe "Attempted to register unchanged model [${id(unchangedModel)}] as changed. It " +
                "carries no change, so a mutation the block meant to make is missing. Call notChanged(model), or " +
                "register the outcome of a mutation that can decline with update(model) { mutate() ?: this } in a " +
                "composable block; in a plain block drop required = true"
            shouldThrow<IllegalArgumentException> { ChangesAccumulator().claimingUnchanged(dirtyModel) }
                .message shouldBe "Attempted to register changed model [${id(dirtyModel)}] as unchanged. It " +
                "carries a write, and notChanged would drop it. Register a new model with add and a changed one " +
                "with update"
            shouldThrow<IllegalArgumentException> { ChangesAccumulator().withResult("nothing", null) }
                .message shouldBe "No changes to persist: the changes { } block registered nothing, which usually " +
                "means a mutation was not passed to add or update and would be lost. Register what the block " +
                "wrote with add or update, and a model it read and kept with notChanged(model). When there is " +
                "nothing to write, return noChanges(result) in place of changes { }, where the unit of work's family " +
                "allows it"
            val readAgain = existingCreatedTestModel(dirtyModel.id(), "noscope", 360, V1).activate()
            val child = ChangesAccumulator.seededFrom(ChangesAccumulator().apply { updating(dirtyModel) }.handOff())
            shouldThrow<IllegalStateException> { child.updating(readAgain) }
                .message shouldBe "Failed to merge changes for model [${id(dirtyModel)}]: the parent or a composed " +
                "child changed this model before, and the instance given to update does not carry those " +
                "changes, so writing it would drop them. It was most likely read again by id. Update the " +
                "instance the parent passed in (constantModelParam), or the one execute or " +
                "roundtrip { p -> p(model) } returned"
        }
    }

    Given("An arbitrary model") {
        val newModel = createdTestModel("name", 420)
        val newModelEvent = TestModelCreated(newModel.id())
        val dirtyModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
            .activate()
        val dirtyModelEvent = TestModelStatusChanged(dirtyModel.id(), CREATED, ACTIVE)
        val unchangedModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        When("Principal calling withResult on empty changes") {
            val attempt = {
                ChangesAccumulator().withResult("nelson mandela", null)
            }

            Then("IllegalArgumentException thrown") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldStartWith "No changes to persist"
            }
        }

        When("Principal calling adding and then withResult") {
            val changes = ChangesAccumulator().apply {
                adding(newModel)
            }.withResult("patrice lumumba", null)

            Then("Changes matching added and result produced") {
                changes.modelChangesToPersist shouldBe listOf(AddModel(newModel))
                changes.result shouldBe "patrice lumumba"
            }
        }

        When("Principal calling adding twice for the same model") {
            val attempt = {
                ChangesAccumulator().apply {
                    adding(newModel)
                    adding(newModel)
                }
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldStartWith "Change for a given " +
                    "model [${newModel.id().stringValue()}] was already registered"
            }
        }

        When("Principal calling adding and then updating for the same model") {
            val attempt = {
                ChangesAccumulator().apply {
                    adding(newModel)
                    // I doubt this could ever happen in regular uow
                    // since model has to be round tripped through db
                    // or hacked in some other way to appear in dirty state
                    // but this container must preserve correct behavior nevertheless
                    updating(existingCreatedTestModel(newModel.id(), "noscope", 360, V1).activate())
                }
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldStartWith "Change for a given " +
                    "model [${newModel.id().stringValue()}] was already registered"
            }
        }

        When("Principal calling adding for not new model") {
            val attempt = { ChangesAccumulator().adding(unchangedModel) }

            Then("IllegalArgumentException thrown") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldStartWith
                    "Attempted to register unchanged model [${unchangedModel.id().stringValue()}] as new"
            }
        }

        When("Principal calling adding for dirty model") {
            val attempt = { ChangesAccumulator().adding(dirtyModel) }

            Then("IllegalArgumentException thrown") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldStartWith
                    "Attempted to register changed model [${dirtyModel.id().stringValue()}] as new"
            }
        }

        When("Principal calling updating and then withResult") {
            val changes = ChangesAccumulator().apply {
                updating(dirtyModel)
            }.withResult("angela davis", null)

            Then("Changes matching updated and result produced") {
                changes.modelChangesToPersist shouldBe listOf(UpdateModel(dirtyModel))
                changes.result shouldBe "angela davis"
            }
        }

        When("Principal calling updating for not updated model") {
            val attempt = { ChangesAccumulator().updating(unchangedModel) }

            Then("IllegalArgumentException thrown") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldStartWith
                    "Attempted to register unchanged model [${unchangedModel.id().stringValue()}] as changed"
            }
        }

        When("Principal calling updating for new model") {
            val attempt = { ChangesAccumulator().updating(newModel) }

            Then("IllegalArgumentException thrown") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldStartWith
                    "Attempted to register new model [${newModel.id().stringValue()}] as changed"
            }
        }

        When("Principal calling updating twice for the same model") {
            val attempt = {
                ChangesAccumulator().apply {
                    updating(dirtyModel)
                    updating(dirtyModel)
                }
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldStartWith "Change for a given " +
                    "model [${dirtyModel.id().stringValue()}] was already registered"
            }
        }

        When("Principal calling claimingUnchanged for the unchanged model and then withResult") {
            val changes = ChangesAccumulator().apply {
                claimingUnchanged(unchangedModel)
            }.withResult("robert mugabe", null)

            Then("NoopModel and result produced") {
                changes.modelChangesToPersist shouldBe listOf(NoopModel(unchangedModel))
                changes.result shouldBe "robert mugabe"
            }
        }

        When("Principal calling claimingUnchanged twice for the same model") {
            val attempt = {
                ChangesAccumulator().apply {
                    claimingUnchanged(unchangedModel)
                    claimingUnchanged(unchangedModel)
                }
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldStartWith "Change for a given " +
                    "model [${unchangedModel.id().stringValue()}] was already registered"
            }
        }

        When("Principal calling claimingUnchanged and then updating for the same model") {
            val attempt = {
                ChangesAccumulator().apply {
                    claimingUnchanged(unchangedModel)
                    updating(unchangedModel.activate())
                }
            }

            Then("IllegalStateException thrown") {
                val exception = shouldThrow<IllegalStateException>(attempt)
                exception.message shouldStartWith "Change for a given " +
                    "model [${unchangedModel.id().stringValue()}] was already registered"
            }
        }

        When("Principal calling claimingUnchanged and then updating for not updated model") {
            val attempt = {
                ChangesAccumulator().apply {
                    claimingUnchanged(unchangedModel)
                    updating(unchangedModel)
                }
            }

            Then("The state check fails before the second registration does") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldStartWith
                    "Attempted to register unchanged model [${unchangedModel.id().stringValue()}] as changed"
            }
        }

        When("Principal calling claimingUnchanged for the dirty model") {
            val attempt = { ChangesAccumulator().claimingUnchanged(dirtyModel) }

            Then("IllegalArgumentException thrown") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldStartWith
                    "Attempted to register changed model [${dirtyModel.id().stringValue()}] as unchanged"
            }
        }

        When("Principal calling claimingUnchanged for the new model") {
            val attempt = { ChangesAccumulator().claimingUnchanged(newModel) }

            Then("IllegalArgumentException thrown") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldStartWith
                    "Attempted to register new model [${newModel.id().stringValue()}] as unchanged"
            }
        }

        And("a block that hands its changes off to a composed child") {
            val model1 = existingCreatedTestModel(randomTestModelId(), "name1", 1337, V1)
                .activate()
            // clean on purpose: an unregistered dirty model in a result fails at the executor
            val model2 = existingCreatedTestModel(randomTestModelId(), "name2", 100500, V1)
            val model3 = existingCreatedTestModel(randomTestModelId(), "name3", 0xBABE, V1)
                .activate()
            val tag = Tag(UUID.randomUUID(), "key", "value")
            val stored = existingCreatedTestModel(randomTestModelId(), "stored", 7, V1)
            val notStarted = "Composed Child did not start from the changes its parent holds now; a child extends " +
                "composable.UnitOfWork or a WrappingUnitOfWork family and not the plain UnitOfWork, it is " +
                "constructed with the ExecutionContext given to the factory, and its parent does not register " +
                "from the params lambda or while the child runs"

            When("the child registers into its copy of the parent's changes") {
                val parent = ChangesAccumulator().apply { adding(newModel) }
                val handOff = parent.handOff()
                val child = ChangesAccumulator.seededFrom(handOff).apply { updating(model1) }
                // a second block over the same hand-off, as a unit of work that runs changes { } twice
                val sibling = ChangesAccumulator.seededFrom(handOff)

                Then("the child holds the parent's changes, and its own write reaches nobody else") {
                    child.changeFor(newModel.id()) shouldBe AddModel(newModel)
                    parent.changeFor(model1.id()) shouldBe null
                    sibling.changeFor(model1.id()) shouldBe null
                }
            }

            When("the parent merges the child back and goes on") {
                val parent = ChangesAccumulator().apply { adding(newModel) }
                val child = ChangesAccumulator.seededFrom(parent.handOff()).apply {
                    updating(model1)
                    addingEntity(tag)
                }
                val childChanges = child.withResult(model2, null)
                parent.merging("Child", childChanges)
                parent.updating(model3)
                parent.deletingEntity(tag)
                val changes = parent.withResult(listOf("Gurbanguly", "Berdimuhamedow"), null)

                Then("the parent holds its own changes and the child's") {
                    changes.modelChangesToPersist shouldBe
                        listOf(AddModel(newModel), UpdateModel(model1), UpdateModel(model3))
                    changes.entityChangesToPersist shouldBe listOf(AddEntity(tag), DeleteEntity(tag))
                    changes.result shouldBe listOf("Gurbanguly", "Berdimuhamedow")
                }
                Then("the child's changes stay as its block ended") {
                    childChanges.modelChangesToPersist shouldBe listOf(AddModel(newModel), UpdateModel(model1))
                    childChanges.entityChangesToPersist shouldBe listOf(AddEntity(tag))
                }
            }

            When("the parent registers after the hand-off, as from the params lambda") {
                val parent = ChangesAccumulator().apply { adding(newModel) }
                val handOff = parent.handOff()
                parent.updating(model3)

                Then("the parent refuses to run the child") {
                    shouldThrow<IllegalStateException> { parent.checkUnchangedSince(handOff, "Child") }
                        .message shouldBe notStarted
                }
            }

            When("the parent registers while the child runs") {
                val parent = ChangesAccumulator().apply { adding(newModel) }
                val handOff = parent.handOff()
                parent.checkUnchangedSince(handOff, "Child")
                val child = ChangesAccumulator.seededFrom(handOff).apply { updating(model1) }
                parent.updating(model3)

                Then("the child does not see that registration") {
                    child.changeFor(model3.id()) shouldBe null
                }
                Then("the seed check refuses the child, and the parent keeps its own changes") {
                    shouldThrow<IllegalStateException> { parent.merging("Child", child.withResult(model2, null)) }
                        .message shouldBe notStarted
                    parent.withResult("kept", null).modelChangesToPersist shouldBe
                        listOf(AddModel(newModel), UpdateModel(model3))
                }
            }

            When("the parent writes while the child runs in any other way") {
                val writes: List<ChangesAccumulator.() -> Unit> = listOf(
                    { adding(createdTestModel("late", 1)) },
                    { claimingUnchanged(stored) },
                    { declining(stored) },
                    { addingEntity(tag) },
                    { updatingEntity(tag) },
                    { deletingEntity(tag) },
                    { deletingEntityByKey(Tag.Key(tag.subjectId, tag.name), Tag::class) },
                    { merging("Stub", stubChanges(stored)) },
                )

                Then("the seed check refuses the child each time") {
                    writes.forEach { write ->
                        val parent = ChangesAccumulator().apply { adding(newModel) }
                        val child = ChangesAccumulator.seededFrom(parent.handOff()).apply { updating(model1) }
                        parent.write()
                        shouldThrow<IllegalStateException> { parent.merging("Child", child.withResult(model2, null)) }
                            .message shouldBe notStarted
                    }
                }
            }
        }

        When("a block writes after its changes were built") {
            val accumulator = ChangesAccumulator().apply { adding(newModel) }
            val changes = accumulator.withResult("built", null)
            val entities = changes.entityChangesToPersist
            val writes: List<ChangesAccumulator.() -> Unit> = listOf(
                { adding(createdTestModel("late", 1)) },
                { updating(dirtyModel) },
                { claimingUnchanged(unchangedModel) },
                { declining(unchangedModel) },
                { addingEntity(Tag(UUID.randomUUID(), "key", "value")) },
                { updatingEntity(Tag(UUID.randomUUID(), "key", "value")) },
                { deletingEntity(Tag(UUID.randomUUID(), "key", "value")) },
                { deletingEntityByKey(Tag.Key(UUID.randomUUID(), "key"), Tag::class) },
                { merging("Stub", stubChanges(unchangedModel)) },
            )

            Then("every write fails, and the changes stay as they were") {
                writes.forEach { write ->
                    shouldThrow<IllegalStateException> { accumulator.write() }.message shouldBe
                        "Attempted to register after the changes { } block ended: its changes are built already, " +
                        "so the registration would be lost. Register a model inside the block that persists it"
                }
                changes.modelChangesToPersist shouldBe listOf(AddModel(newModel))
                changes.entityChangesToPersist shouldBe listOf()
                entities shouldBe listOf()
            }
        }

        When("the flattened changes are read between writes") {
            val parent = ChangesAccumulator()
            val reads = mutableListOf<List<ModelChange>>()
            reads.add(parent.modelChangesToPersist)
            parent.adding(newModel)
            reads.add(parent.modelChangesToPersist)
            parent.updating(dirtyModel)
            reads.add(parent.modelChangesToPersist)
            parent.claimingUnchanged(unchangedModel)
            reads.add(parent.modelChangesToPersist)
            val child = ChangesAccumulator.seededFrom(parent.handOff())
            val activated = newModel.activate()
            reads.add(child.modelChangesToPersist)
            child.updating(activated)
            reads.add(child.modelChangesToPersist)
            val coveredBefore = parent.persistsWritesOf(activated)
            parent.merging("Child", child.withResult("merged", null))
            reads.add(parent.modelChangesToPersist)

            Then("every read shows every write before it") {
                reads shouldBe listOf(
                    listOf(),
                    listOf(AddModel(newModel)),
                    listOf(AddModel(newModel), UpdateModel(dirtyModel)),
                    listOf(AddModel(newModel), UpdateModel(dirtyModel), NoopModel(unchangedModel)),
                    listOf(AddModel(newModel), UpdateModel(dirtyModel), NoopModel(unchangedModel)),
                    listOf(AddModel(activated), UpdateModel(dirtyModel), NoopModel(unchangedModel)),
                    listOf(AddModel(activated), UpdateModel(dirtyModel), NoopModel(unchangedModel)),
                )
                coveredBefore shouldBe false
                parent.persistsWritesOf(activated) shouldBe true
            }
        }
    }

    Given("ChangesAccumulator query and merge methods") {
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
            val acc = ChangesAccumulator().apply { adding(model1) }
            val result = acc.changeFor(model1.id())

            Then("the change is returned") {
                result shouldBe AddModel(model1)
            }
        }

        When("changeFor is called for non-existing model") {
            val acc = ChangesAccumulator().apply { adding(model1) }
            val result = acc.changeFor(model2.id())

            Then("null is returned") {
                result shouldBe null
            }
        }

        When("updating is called for an inherited added model") {
            val acc = ChangesAccumulator.seededFrom(ChangesAccumulator().apply { adding(model1) }.handOff())
            val updatedModel1 = model1.activate()
            val statusChanged = TestModelStatusChanged(model1.id(), CREATED, ACTIVE)
            val result = acc.apply { updating(updatedModel1) }.withResult("replaced", null)

            Then("the change is replaced and stays an add") {
                result.modelChangesToPersist shouldBe
                    listOf(AddModel(updatedModel1))
                result.result shouldBe "replaced"
            }
        }

        When("updating is called for an inherited updated model") {
            val read = existingCreatedTestModel(randomTestModelId(), "read", 300, V1)
            val renamed = read.changeParam1("renamed")
            val activated = renamed.activate()
            val acc = ChangesAccumulator.seededFrom(ChangesAccumulator().apply { updating(renamed) }.handOff())
            val result = acc.apply { updating(activated) }.withResult("replaced", null)

            Then("the change is replaced and stays an update") {
                result.modelChangesToPersist shouldBe listOf(UpdateModel(activated))
                result.modelChangesToPersist.single().modelEvents shouldHaveSize 2
            }
        }
    }

    Given("Entities") {
        val subjectId = UUID.randomUUID()
        val tag1 = Tag(subjectId, "key1", "value1")
        val tag2 = Tag(subjectId, "key2", "value2")
        val employeeId = EmployeeId(UUID.randomUUID())
        val allocation = RationAllocation(employeeId, BUBALEH, LocalDate.now(), 5)

        When("Principal calling addingEntity and then withResult") {
            val changes = ChangesAccumulator().apply {
                addingEntity(tag1)
            }.withResult("tag added", null)

            Then("Changes matching added entity and result produced") {
                changes.entityChangesToPersist shouldBe listOf(AddEntity(tag1))
                changes.modelChangesToPersist shouldBe emptyList()
                changes.result shouldBe "tag added"
            }
        }

        When("Principal calling addingEntity twice for different entities") {
            val changes = ChangesAccumulator().apply {
                addingEntity(tag1)
                addingEntity(tag2)
            }.withResult("tags added", null)

            Then("Changes contain both entities") {
                changes.entityChangesToPersist shouldBe listOf(AddEntity(tag1), AddEntity(tag2))
                changes.result shouldBe "tags added"
            }
        }

        When("Principal calling addingEntity twice for the same entity") {
            val changes = ChangesAccumulator().apply {
                addingEntity(tag1)
                addingEntity(tag1)
            }.withResult("duplicate tag", null)

            Then("Changes contain duplicate entities (no deduplication)") {
                changes.entityChangesToPersist shouldBe listOf(AddEntity(tag1), AddEntity(tag1))
                changes.result shouldBe "duplicate tag"
            }
        }

        When("Principal calling deletingEntity and then withResult") {
            val changes = ChangesAccumulator().apply {
                deletingEntity(tag1)
            }.withResult("tag deleted", null)

            Then("Changes matching deleted entity and result produced") {
                changes.entityChangesToPersist shouldBe listOf(DeleteEntity(tag1))
                changes.result shouldBe "tag deleted"
            }
        }

        When("Principal calling addingEntity and deletingEntity") {
            val changes = ChangesAccumulator().apply {
                addingEntity(tag1)
                deletingEntity(tag2)
            }.withResult("mixed entity ops", null)

            Then("Changes contain both add and delete") {
                changes.entityChangesToPersist shouldBe listOf(AddEntity(tag1), DeleteEntity(tag2))
                changes.result shouldBe "mixed entity ops"
            }
        }

        When("Principal calling updatingEntity and then withResult") {
            val changes = ChangesAccumulator().apply {
                updatingEntity(tag1)
            }.withResult("tag updated", null)

            Then("Changes matching updated entity and result produced") {
                changes.entityChangesToPersist shouldBe listOf(UpdateEntity(tag1))
                changes.modelChangesToPersist shouldBe listOf()
                changes.result shouldBe "tag updated"
            }
        }

        When("Principal calling updatingEntity twice for different entities") {
            val changes = ChangesAccumulator().apply {
                updatingEntity(tag1)
                updatingEntity(tag2)
            }.withResult("tags updated", null)

            Then("Changes contain both entities") {
                changes.entityChangesToPersist shouldBe listOf(UpdateEntity(tag1), UpdateEntity(tag2))
                changes.result shouldBe "tags updated"
            }
        }

        When("Principal calling updatingEntity twice for the same entity") {
            val changes = ChangesAccumulator().apply {
                updatingEntity(tag1)
                updatingEntity(tag1)
            }.withResult("duplicate tag update", null)

            Then("Changes contain duplicate entities (no deduplication)") {
                changes.entityChangesToPersist shouldBe listOf(UpdateEntity(tag1), UpdateEntity(tag1))
                changes.result shouldBe "duplicate tag update"
            }
        }

        When("Principal calling addingEntity and updatingEntity and deletingEntity") {
            val tag3 = Tag(subjectId, "key3", "value3")
            val changes = ChangesAccumulator().apply {
                addingEntity(tag1)
                updatingEntity(tag2)
                deletingEntity(tag3)
            }.withResult("all entity ops", null)

            Then("Changes contain add, update and delete") {
                changes.entityChangesToPersist shouldBe listOf(
                    AddEntity(tag1),
                    UpdateEntity(tag2),
                    DeleteEntity(tag3),
                )
                changes.result shouldBe "all entity ops"
            }
        }

        When("Principal calling addingEntity for CreatableEntity (not DeletableEntity)") {
            val changes = ChangesAccumulator().apply {
                addingEntity(allocation)
            }.withResult("allocation added", null)

            Then("Changes matching added allocation and result produced") {
                changes.entityChangesToPersist shouldBe listOf(AddEntity(allocation))
                changes.result shouldBe "allocation added"
            }
        }

        And("A model") {
            val model = createdTestModel("test", 100)
            val modelEvent = TestModelCreated(model.id())

            When("Principal adds both model and entity") {
                val changes = ChangesAccumulator().apply {
                    adding(model)
                    addingEntity(tag1)
                }.withResult("mixed changes", null)

                Then("Changes contain both model and entity changes") {
                    changes.modelChangesToPersist shouldBe listOf(AddModel(model))
                    changes.entityChangesToPersist shouldBe listOf(AddEntity(tag1))
                    changes.result shouldBe "mixed changes"
                }
            }

            When("Principal updates both model and entity") {
                val existingModel = existingCreatedTestModel(param1 = "existing", param2 = 1L).activate()
                val statusEvent = TestModelStatusChanged(existingModel.id(), CREATED, ACTIVE)
                val changes = ChangesAccumulator().apply {
                    updating(existingModel)
                    updatingEntity(tag1)
                }.withResult("mixed updates", null)

                Then("Changes contain both model and entity updates") {
                    changes.modelChangesToPersist shouldBe listOf(
                        UpdateModel(existingModel),
                    )
                    changes.entityChangesToPersist shouldBe listOf(UpdateEntity(tag1))
                    changes.result shouldBe "mixed updates"
                }
            }
        }
    }

    Given("Child models flattening") {
        val bossId = EmployeeId()

        When("Aggregate with new child models is added") {
            val deptWithEmployee = newDeptAggregate(
                name = "Engineering",
                boss = bossId,
                ration = BUBALEH,
            ).let { d ->
                val emp = mutating { newEmployee(Name("Alice", "Smith"), d.id(), "alice@test.com", BUBALEH) }
                d.addEmployee(emp)
            }
            val changes = ChangesAccumulator().apply {
                adding(deptWithEmployee)
            }.withResult("dept added", null)

            Then("Parent and child are flattened into separate changes") {
                changes.modelChangesToPersist shouldHaveSize 2

                val parentChange = changes.modelChangesToPersist[0]
                parentChange.shouldBeInstanceOf<AddModel<*, *>>()
                parentChange.model shouldBe deptWithEmployee

                val childChange = changes.modelChangesToPersist[1]
                childChange.shouldBeInstanceOf<AddModel<*, *>>()
                childChange.model shouldBe deptWithEmployee.employees[0]
            }

            Then("Child events are on the child change, not on the parent") {
                val parentChange = changes.modelChangesToPersist[0]
                val childChange = changes.modelChangesToPersist[1]

                // Parent has its own events (OwnedDepartmentCreated + EmployeeAdded)
                parentChange.modelEvents.any { it is OwnedDepartmentCreated } shouldBe true

                // Child has its own event (EmployeeCreated)
                childChange.modelEvents shouldHaveSize 1
                childChange.modelEvents[0].shouldBeInstanceOf<EmployeeCreated>()
            }
        }

        When("Aggregate without children is added") {
            val dept = newDeptAggregate(
                name = "Empty Dept",
                boss = bossId,
                ration = BUBALEH,
            )
            val changes = ChangesAccumulator().apply {
                adding(dept)
            }.withResult("no children", null)

            Then("Only parent change is produced") {
                changes.modelChangesToPersist shouldHaveSize 1
                changes.modelChangesToPersist[0].model shouldBe dept
            }
        }

        When("Child model is explicitly registered and also in parent's ownedModels") {
            val dept = newDeptAggregate(
                name = "Engineering",
                boss = bossId,
                ration = BUBALEH,
            )
            val emp = mutating { newEmployee(Name("Bob", "Jones"), dept.id(), "bob@test.com", BUBALEH) }
            val deptWithEmp = dept.addEmployee(emp)

            val changes = ChangesAccumulator().apply {
                adding(emp)
                adding(deptWithEmp)
            }.withResult("explicit child", null)

            Then("Child is not duplicated - explicit registration takes precedence") {
                changes.modelChangesToPersist shouldHaveSize 2
                changes.modelChangesToPersist[0].model shouldBe emp
                changes.modelChangesToPersist[1].model shouldBe deptWithEmp
            }
        }

        When("An aggregate owns a later instance of a child registered on its own") {
            val dept = newDeptAggregate(name = "Engineering", boss = bossId, ration = BUBALEH)
            val emp = mutating { newEmployee(Name("Dan", "Brown"), dept.id(), "dan@test.com", BUBALEH) }
            val otherDept = OwnedDepartment(
                DepartmentId.randomDepartmentId(), "Other", bossId, 1, BUBALEH, persistentState(V1, null),
            )
            val moved = mutating { emp.changeDepartment(otherDept) }
            val owner = dept.addEmployee(moved)
            val changes = ChangesAccumulator().apply {
                adding(emp)
                adding(owner)
            }.withResult("later", null)

            Then("The later instance persists once, in the earlier registration's place") {
                changes.modelChangesToPersist.map { it.model } shouldBe listOf(moved, owner)
                changes.modelChangesToPersist[0].shouldBeInstanceOf<AddModel<*, *>>()
                changes.modelChangesToPersist[0].modelEvents shouldHaveSize 2
            }
        }

        When("An aggregate owns an instance that diverges from the registered one") {
            val dept = newDeptAggregate(name = "Engineering", boss = bossId, ration = BUBALEH)
            val emp = mutating { newEmployee(Name("Fay", "Black"), dept.id(), "fay@test.com", BUBALEH) }
            fun elsewhere(name: String) = OwnedDepartment(
                DepartmentId.randomDepartmentId(), name, bossId, 1, BUBALEH, persistentState(V1, null),
            )
            val registered = mutating { emp.changeDepartment(elsewhere("One")) }
            val owned = mutating { emp.changeDepartment(elsewhere("Two")) }
            val accumulator = ChangesAccumulator().apply {
                adding(registered)
                adding(dept.addEmployee(owned))
            }

            Then("Flattening fails instead of dropping one of the two writes") {
                val ex = shouldThrow<IllegalStateException> { accumulator.withResult("diverged", null) }
                checkNotNull(ex.message) shouldContain "that diverges from the one it persists as"
            }
        }

        When("Two aggregates own one employee, one clean and one moved") {
            val employee = Employee(
                EmployeeId(), Name("Gus", "Grey"), DepartmentId.randomDepartmentId(), "gus@test.com", BUBALEH,
                persistentState(V1, null),
            )
            val otherDept = OwnedDepartment(
                DepartmentId.randomDepartmentId(), "Other", bossId, 1, BUBALEH, persistentState(V1, null),
            )
            val moved = mutating { employee.changeDepartment(otherDept) }
            fun owning(owned: Employee, name: String) = DeptAggregate(
                DepartmentId.randomDepartmentId(), name, bossId, 1, BUBALEH, listOf(owned), persistentState(V1, null),
            )
            val keeper = owning(employee, "Keeper")
            val mover = owning(moved, "Mover").rename("Mover v2")

            Then("The move persists once whichever owner is registered first") {
                listOf(
                    ChangesAccumulator().apply {
                        claimingUnchanged(keeper)
                        updating(mover)
                    },
                    ChangesAccumulator().apply {
                        updating(mover)
                        claimingUnchanged(keeper)
                    },
                ).forEach { accumulator ->
                    val employeeChanges = accumulator.withResult("both", null).modelChangesToPersist
                        .filter { it.id == employee.id() }
                    employeeChanges.map { it.model } shouldBe listOf(moved)
                    employeeChanges.single().shouldBeInstanceOf<UpdateModel<*, *>>()
                }
            }
        }

        When("Two aggregates own diverging moves of one employee that nothing registers on its own") {
            val employee = Employee(
                EmployeeId(), Name("Hal", "Ring"), DepartmentId.randomDepartmentId(), "hal@test.com", BUBALEH,
                persistentState(V1, null),
            )
            fun movedTo(name: String) = mutating {
                employee.changeDepartment(
                    OwnedDepartment(
                        DepartmentId.randomDepartmentId(), name, bossId, 1, BUBALEH, persistentState(V1, null),
                    ),
                )
            }
            fun owning(owned: Employee, name: String) = DeptAggregate(
                DepartmentId.randomDepartmentId(), name, bossId, 1, BUBALEH, listOf(owned), persistentState(V1, null),
            ).rename("$name v2")
            val accumulator = ChangesAccumulator().apply {
                updating(owning(movedTo("One"), "A"))
                updating(owning(movedTo("Two"), "B"))
            }

            Then("Flattening fails instead of persisting one move and dropping the other") {
                val ex = shouldThrow<IllegalStateException> { accumulator.withResult("diverged", null) }
                checkNotNull(ex.message) shouldContain "that diverges from the one it persists as"
            }
        }

        When("A wrapper owns a later state of a department whose own registration holds an employee's move") {
            val employee = Employee(
                EmployeeId(), Name("Ivy", "Lane"), DepartmentId.randomDepartmentId(), "ivy@test.com", BUBALEH,
                persistentState(V1, null),
            )
            val moved = mutating {
                employee.changeDepartment(
                    OwnedDepartment(
                        DepartmentId.randomDepartmentId(), "Away", bossId, 1, BUBALEH, persistentState(V1, null),
                    ),
                )
            }
            val dept = DeptAggregate(
                DepartmentId.randomDepartmentId(), "Dept", bossId, 1, BUBALEH, listOf(moved), persistentState(V1, null),
            ).rename("Dept v2")
            // a later state of the department that still lists the employee as it was read
            val deptLater = DeptAggregate(
                dept.id(), "Dept v3", bossId, 1, BUBALEH, listOf(employee),
                dept.raise(DepartmentEvent.NameChanged(dept.id(), dept.name, "Dept v3")),
            )
            val box = Box(BoxId(java.util.UUID.randomUUID()), persistentState(V1, null), deptLater)

            Then("The move persists whichever is registered first") {
                listOf(
                    ChangesAccumulator().apply {
                        claimingUnchanged(box)
                        updating(dept)
                    },
                    ChangesAccumulator().apply {
                        updating(dept)
                        claimingUnchanged(box)
                    },
                ).forEach { accumulator ->
                    val persisted = accumulator.withResult("nested", null).modelChangesToPersist
                    persisted.single { it.id == employee.id() }.model shouldBe moved
                    persisted.single { it.id == dept.id() }.model shouldBe deptLater
                }
            }
        }

        When("An aggregate owns an earlier instance of a child registered on its own") {
            val dept = newDeptAggregate(name = "Engineering", boss = bossId, ration = BUBALEH)
            val emp = mutating { newEmployee(Name("Eve", "Green"), dept.id(), "eve@test.com", BUBALEH) }
            val otherDept = OwnedDepartment(
                DepartmentId.randomDepartmentId(), "Other", bossId, 1, BUBALEH, persistentState(V1, null),
            )
            val moved = mutating { emp.changeDepartment(otherDept) }
            val changes = ChangesAccumulator().apply {
                adding(moved)
                adding(dept.addEmployee(emp))
            }.withResult("covered", null)

            Then("The registered instance persists the child, once") {
                changes.modelChangesToPersist.map { it.model } shouldBe listOf(moved, dept.addEmployee(emp))
            }
        }

        When("Updated parent has new child models") {
            val existingDept = DeptAggregate(
                id = DepartmentId.randomDepartmentId(),
                name = "Engineering",
                boss = bossId,
                headcount = 1,
                ration = BUBALEH,
                employees = listOf(),
                modelState = persistentState(V1, null),
            )
            val emp = mutating { newEmployee(Name("Carol", "White"), existingDept.id(), "carol@test.com", BUBALEH) }
            val renamedWithEmp = existingDept.rename("Eng v2").addEmployee(emp)

            val changes = ChangesAccumulator().apply {
                updating(renamedWithEmp)
            }.withResult("updated parent new child", null)

            Then("UpdateModel for parent and AddModel for new child") {
                changes.modelChangesToPersist shouldHaveSize 2

                val parentChange = changes.modelChangesToPersist[0]
                parentChange.shouldBeInstanceOf<UpdateModel<*, *>>()
                parentChange.model shouldBe renamedWithEmp

                val childChange = changes.modelChangesToPersist[1]
                childChange.shouldBeInstanceOf<AddModel<*, *>>()
                childChange.model shouldBe emp
            }
        }

        When("Parent carries persistent child that is not dirty") {
            val existingEmp = Employee(
                id = EmployeeId(),
                name = Name("Dave", "Brown"),
                departmentId = DepartmentId.randomDepartmentId(),
                email = "dave@test.com",
                ration = BUBALEH,
                modelState = persistentState(V1, null),
            )
            val existingDept = DeptAggregate(
                id = existingEmp.departmentId,
                name = "Engineering",
                boss = bossId,
                headcount = 2,
                ration = BUBALEH,
                employees = listOf(existingEmp),
                modelState = persistentState(V1, null),
            )
            val renamedDept = existingDept.rename("Eng v3")

            val changes = ChangesAccumulator().apply {
                updating(renamedDept)
            }.withResult("persistent child skipped", null)

            Then("Only UpdateModel for parent, persistent child is skipped") {
                changes.modelChangesToPersist shouldHaveSize 1

                val parentChange = changes.modelChangesToPersist[0]
                parentChange.shouldBeInstanceOf<UpdateModel<*, *>>()
                parentChange.model shouldBe renamedDept
            }
        }

        When("An owned child's write is claimed as unchanged by the block") {
            val emp = mutating {
                newEmployee(Name("Frank", "Black"), DepartmentId.randomDepartmentId(), "f@test.com", BUBALEH)
            }
            val claimed = Employee(
                id = emp.id(),
                name = emp.name,
                departmentId = emp.departmentId,
                email = emp.email,
                ration = BUBALEH,
                modelState = persistentState(V1, null),
            )
            val dept = DeptAggregate(
                id = emp.departmentId,
                name = "Engineering",
                boss = bossId,
                headcount = 2,
                ration = BUBALEH,
                employees = listOf(emp),
                modelState = persistentState(V1, null),
            ).rename("Eng v4")

            Then("The contradiction fails loudly instead of dropping the child's write") {
                val exception = shouldThrow<IllegalStateException> {
                    ChangesAccumulator().apply {
                        claimingUnchanged(claimed)
                        updating(dept)
                    }.withResult("claim hides the child", null)
                }
                exception.message shouldBe "Model [${emp.id().stringValue()}] is registered as " +
                    "unchanged, but aggregate [${dept.id().stringValue()}] owns a new instance of " +
                    "it: the write would be silently dropped"
            }
        }

        When("Nested aggregates (2-level deep) are added") {
            val deptWithEmployee = newDeptAggregate(
                name = "Inner Dept",
                boss = bossId,
                ration = BUBALEH,
            ).let { d ->
                val emp = mutating { newEmployee(Name("Eve", "Green"), d.id(), "eve@test.com", BUBALEH) }
                d.addEmployee(emp)
            }
            val wrapperId = DepartmentId.randomDepartmentId()
            val wrapper = WrapperAggregate(
                id = wrapperId,
                modelState = newState(
                    OwnedDepartmentCreated(wrapperId, "wrapper", bossId, 1, BUBALEH),
                ),
                ownedModels = listOf(deptWithEmployee),
            )

            val changes = ChangesAccumulator().apply {
                adding(wrapper)
            }.withResult("nested 2 levels", null)

            Then("All 3 models appear: wrapper, dept, employee") {
                changes.modelChangesToPersist shouldHaveSize 3

                val wrapperChange = changes.modelChangesToPersist[0]
                wrapperChange.shouldBeInstanceOf<AddModel<*, *>>()
                wrapperChange.model shouldBe wrapper

                val deptChange = changes.modelChangesToPersist[1]
                deptChange.shouldBeInstanceOf<AddModel<*, *>>()
                deptChange.model shouldBe deptWithEmployee

                val empChange = changes.modelChangesToPersist[2]
                empChange.shouldBeInstanceOf<AddModel<*, *>>()
                empChange.model shouldBe deptWithEmployee.employees[0]
            }
        }
    }
}) {
    private class WrapperAggregate(
        id: DepartmentId,
        modelState: ModelState<DepartmentId, DepartmentEvent>,
        ownedModels: List<Model<*, *>> = listOf(),
    ) : Aggregate<DepartmentId, DepartmentEvent>(id, modelState, ownedModels)
}

private data class BoxId(override val id: java.util.UUID) : com.razz.eva.domain.ModelId<java.util.UUID>

private class BoxEvent(override val modelId: BoxId) : com.razz.eva.domain.ModelEvent<BoxId> {
    override val modelName = "Box"
}

// an aggregate that owns an aggregate, for the nested flatten case
private class Box(
    id: BoxId,
    modelState: com.razz.eva.domain.ModelState<BoxId, BoxEvent>,
    dept: DeptAggregate<List<Employee>>,
) : com.razz.eva.domain.Aggregate<BoxId, BoxEvent>(id, modelState, listOf(dept))
