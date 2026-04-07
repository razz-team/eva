package com.razz.eva.uow

import com.razz.eva.domain.Aggregate
import com.razz.eva.domain.DepartmentEvent
import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.DepartmentId
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
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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

        When("from is called with Changes that include updated entity") {
            val subjectId2 = UUID.randomUUID()
            val updatedTag = Tag(subjectId2, "key", "updated-value")
            val changes = ChangesAccumulator()
                .withUpdatedEntity(updatedTag)
                .withResult("with-updated-entity")

            val restored = ChangesAccumulator.from(changes).withResult("restored")

            Then("accumulator contains updated entity change") {
                restored.entityChangesToPersist shouldBe listOf(UpdateEntity(updatedTag))
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

        When("Principal calling withUpdatedEntity and then withResult") {
            val changes = ChangesAccumulator()
                .withUpdatedEntity(tag1)
                .withResult("tag updated")

            Then("Changes matching updated entity and result produced") {
                changes.entityChangesToPersist shouldBe listOf(UpdateEntity(tag1))
                changes.modelChangesToPersist shouldBe listOf()
                changes.result shouldBe "tag updated"
            }
        }

        When("Principal calling withUpdatedEntity twice for different entities") {
            val changes = ChangesAccumulator()
                .withUpdatedEntity(tag1)
                .withUpdatedEntity(tag2)
                .withResult("tags updated")

            Then("Changes contain both entities") {
                changes.entityChangesToPersist shouldBe listOf(UpdateEntity(tag1), UpdateEntity(tag2))
                changes.result shouldBe "tags updated"
            }
        }

        When("Principal calling withUpdatedEntity twice for the same entity") {
            val changes = ChangesAccumulator()
                .withUpdatedEntity(tag1)
                .withUpdatedEntity(tag1)
                .withResult("duplicate tag update")

            Then("Changes contain duplicate entities (no deduplication)") {
                changes.entityChangesToPersist shouldBe listOf(UpdateEntity(tag1), UpdateEntity(tag1))
                changes.result shouldBe "duplicate tag update"
            }
        }

        When("Principal calling withAddedEntity and withUpdatedEntity and withDeletedEntity") {
            val tag3 = Tag(subjectId, "key3", "value3")
            val changes = ChangesAccumulator()
                .withAddedEntity(tag1)
                .withUpdatedEntity(tag2)
                .withDeletedEntity(tag3)
                .withResult("all entity ops")

            Then("Changes contain add, update and delete") {
                changes.entityChangesToPersist shouldBe listOf(
                    AddEntity(tag1),
                    UpdateEntity(tag2),
                    DeleteEntity(tag3),
                )
                changes.result shouldBe "all entity ops"
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

            When("Principal updates both model and entity") {
                val existingModel = existingCreatedTestModel(param1 = "existing", param2 = 1L).activate()
                val statusEvent = TestModelStatusChanged(existingModel.id(), CREATED, ACTIVE)
                val changes = ChangesAccumulator()
                    .withUpdatedModel(existingModel)
                    .withUpdatedEntity(tag1)
                    .withResult("mixed updates")

                Then("Changes contain both model and entity updates") {
                    changes.modelChangesToPersist shouldBe listOf(
                        UpdateModel(existingModel, listOf(statusEvent)),
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
                val emp = newEmployee(Name("Alice", "Smith"), d.id(), "alice@test.com", BUBALEH)
                d.addEmployee(emp)
            }
            val changes = ChangesAccumulator()
                .withAddedModel(deptWithEmployee)
                .withResult("dept added")

            Then("Parent and child are flattened into separate changes") {
                changes.modelChangesToPersist shouldHaveSize 2

                val parentChange = changes.modelChangesToPersist[0]
                parentChange.shouldBeInstanceOf<AddModel<*, *, *>>()
                parentChange.model shouldBe deptWithEmployee

                val childChange = changes.modelChangesToPersist[1]
                childChange.shouldBeInstanceOf<AddModel<*, *, *>>()
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
            val changes = ChangesAccumulator()
                .withAddedModel(dept)
                .withResult("no children")

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
            val emp = newEmployee(Name("Bob", "Jones"), dept.id(), "bob@test.com", BUBALEH)
            val deptWithEmp = dept.addEmployee(emp)

            val changes = ChangesAccumulator()
                .withAddedModel(emp)
                .withAddedModel(deptWithEmp)
                .withResult("explicit child")

            Then("Child is not duplicated - explicit registration takes precedence") {
                changes.modelChangesToPersist shouldHaveSize 2
                changes.modelChangesToPersist[0].model shouldBe emp
                changes.modelChangesToPersist[1].model shouldBe deptWithEmp
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
            val emp = newEmployee(Name("Carol", "White"), existingDept.id(), "carol@test.com", BUBALEH)
            val renamedWithEmp = existingDept.rename("Eng v2").addEmployee(emp)

            val changes = ChangesAccumulator()
                .withUpdatedModel(renamedWithEmp)
                .withResult("updated parent new child")

            Then("UpdateModel for parent and AddModel for new child") {
                changes.modelChangesToPersist shouldHaveSize 2

                val parentChange = changes.modelChangesToPersist[0]
                parentChange.shouldBeInstanceOf<UpdateModel<*, *, *>>()
                parentChange.model shouldBe renamedWithEmp

                val childChange = changes.modelChangesToPersist[1]
                childChange.shouldBeInstanceOf<AddModel<*, *, *>>()
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

            val changes = ChangesAccumulator()
                .withUpdatedModel(renamedDept)
                .withResult("persistent child skipped")

            Then("Only UpdateModel for parent, persistent child is skipped") {
                changes.modelChangesToPersist shouldHaveSize 1

                val parentChange = changes.modelChangesToPersist[0]
                parentChange.shouldBeInstanceOf<UpdateModel<*, *, *>>()
                parentChange.model shouldBe renamedDept
            }
        }

        When("Nested aggregates (2-level deep) are added") {
            val deptWithEmployee = newDeptAggregate(
                name = "Inner Dept",
                boss = bossId,
                ration = BUBALEH,
            ).let { d ->
                val emp = newEmployee(Name("Eve", "Green"), d.id(), "eve@test.com", BUBALEH)
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

            val changes = ChangesAccumulator()
                .withAddedModel(wrapper)
                .withResult("nested 2 levels")

            Then("All 3 models appear: wrapper, dept, employee") {
                changes.modelChangesToPersist shouldHaveSize 3

                val wrapperChange = changes.modelChangesToPersist[0]
                wrapperChange.shouldBeInstanceOf<AddModel<*, *, *>>()
                wrapperChange.model shouldBe wrapper

                val deptChange = changes.modelChangesToPersist[1]
                deptChange.shouldBeInstanceOf<AddModel<*, *, *>>()
                deptChange.model shouldBe deptWithEmployee

                val empChange = changes.modelChangesToPersist[2]
                empChange.shouldBeInstanceOf<AddModel<*, *, *>>()
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
