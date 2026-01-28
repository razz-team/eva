package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.domain.Version.Companion.version
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
                ex.message shouldBe "Attempted to register unchanged model [${persistedModel.id()}] as changed"
            }
        }

        When("Principal tries to ADD persisted model via ModelChange") {
            val change = AddModel(persistedModel, listOf())
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register unchanged model [${persistedModel.id()}] as new"
            }
        }

        When("Principal tries to merge NoopModel change with AddModel change") {
            val noop = NoopModel(persistedModel)
            val newModel = createdTestModel("noscope", 360).activate()
            val events = newModel.writeEvents(ModelEventDrive()).events()
            val add = AddModel(newModel, events)
            val merged = noop.merge(add)

            Then("Merged change is AddModel") {
                merged shouldBe add
            }
        }

        When("Principal tries to merge NoopModel change with UpdateModel change") {
            val noop = NoopModel(persistedModel)
            val dirtyModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1).activate()
            val events = dirtyModel.writeEvents(ModelEventDrive()).events()
            val update = UpdateModel(dirtyModel, events)
            val merged = noop.merge(update)

            Then("Merged change is UpdateModel") {
                merged shouldBe update
            }
        }

        When("Principal tries to merge NoopModel change with NoopModel change") {
            val noop = NoopModel(persistedModel)
            val anotherNoop = NoopModel(persistedModel)
            val merged = noop.merge(anotherNoop)

            Then("Merged change is another NoopModel") {
                merged shouldBe anotherNoop
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
                ex.message shouldBe "Attempted to register changed model [${dirtyModel.id()}] as new"
            }
        }

        When("Principal tries mark dirty model as not changed via ModelChange") {
            val change = NoopModel(dirtyModel)
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register changed model [${dirtyModel.id()}] as unchanged"
            }
        }

        When("Principal tries to merge UpdateModel change with AddModel change") {
            val update = UpdateModel(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            val newModel = createdTestModel("noscope", 360).activate()
            val add = AddModel(newModel, newModel.writeEvents(ModelEventDrive()).events())
            val merged = update.merge(add)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }

        When("Principal tries to merge UpdateModel change with NoopModel change") {
            val update = UpdateModel(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            val persistedModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
            val noop = NoopModel(persistedModel)
            val merged = update.merge(noop)

            Then("Merged change is null") {
                merged shouldBe update
            }
        }

        When("Principal tries to merge UpdateModel change with UpdateModel change") {
            val update = UpdateModel(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            val updatedModel = dirtyModel.activate()
            val anotherUpdate = UpdateModel(updatedModel, updatedModel.writeEvents(ModelEventDrive()).events())
            val merged = update.merge(anotherUpdate)

            Then("Merged change is UpdateModel") {
                merged shouldBe anotherUpdate
            }
        }

        When("Principal tries to merge UpdateModel change with UpdateModel change for different event") {
            val firstAcivate = dirtyModel.activate()
            val update = UpdateModel(firstAcivate, firstAcivate.writeEvents(ModelEventDrive()).events())
            val updatedModel = dirtyModel.activate()
            val anotherUpdate = UpdateModel(updatedModel, updatedModel.writeEvents(ModelEventDrive()).events())
            val merged = update.merge(anotherUpdate)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }

        When("Principal tries to merge UpdateModel change with UpdateModel change for different model") {
            val update = UpdateModel(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            val incompatibleModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
                .changeParam1("mlg")
            val incompatibleUpdate =
                UpdateModel(incompatibleModel, incompatibleModel.writeEvents(ModelEventDrive()).events())
            val merged = update.merge(incompatibleUpdate)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }

        When("Principal tries to merge UpdateModel change with UpdateModel change model of different version") {
            val update = UpdateModel(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            val incompatibleModel = existingCreatedTestModel(dirtyModel.id(), "noscope", 360, version(2))
                .changeParam1("mlg")
            val incompatibleUpdate =
                UpdateModel(incompatibleModel, incompatibleModel.writeEvents(ModelEventDrive()).events())
            val merged = update.merge(incompatibleUpdate)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }

        When("Principal tries to merge UpdateModel change with UpdateModel change model with non successive events") {
            val update = UpdateModel(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            val incompatibleModel = existingCreatedTestModel(dirtyModel.id(), "noscope", 360, V1).activate()
            val incompatibleUpdate =
                UpdateModel(incompatibleModel, incompatibleModel.writeEvents(ModelEventDrive()).events())
            val merged = update.merge(incompatibleUpdate)

            Then("Merged change is null") {
                merged shouldBe null
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
                ex.message shouldBe "Attempted to register new model [${newModel.id()}] as changed"
            }
        }

        When("Principal tries mark new model as not changed via ModelChange") {
            val change = NoopModel(newModel)
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register new model [${newModel.id()}] as unchanged"
            }
        }

        When("Principal tries to merge AddModel change with AddModel change") {
            val add = AddModel(newModel, newModel.writeEvents(ModelEventDrive()).events())
            val anotherNewModel = createdTestModel("noscope", 360).activate()
            val anotherAdd = AddModel(anotherNewModel, anotherNewModel.writeEvents(ModelEventDrive()).events())
            val merged = add.merge(anotherAdd)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }

        When("Principal tries to merge AddModel change with NoopModel change") {
            val add = AddModel(newModel, newModel.writeEvents(ModelEventDrive()).events())
            val persistedModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
            val noop = NoopModel(persistedModel)
            val merged = add.merge(noop)

            Then("Merged change is AddModel") {
                merged shouldBe add
            }
        }

        When("Principal tries to merge AddModel change with new model wrapped in UpdateModel change") {
            val add = AddModel(newModel, newModel.writeEvents(ModelEventDrive()).events())
            val dirtyModel = newModel.changeParam1("mlg").activate()
            val update = UpdateModel(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            val merged = add.merge(update)

            Then("Merged change is UpdateModel") {
                merged shouldBe AddModel(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            }
        }

        When("Principal tries to merge AddModel change with UpdateModel change for different model") {
            val add = AddModel(newModel, newModel.writeEvents(ModelEventDrive()).events())
            val incompatibleModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
                .changeParam1("mlg")
            val incompatibleUpdate =
                UpdateModel(incompatibleModel, incompatibleModel.writeEvents(ModelEventDrive()).events())
            val merged = add.merge(incompatibleUpdate)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }

        When("Principal tries to merge AddModel change with UpdateModel change model of different version") {
            val add = AddModel(newModel, newModel.writeEvents(ModelEventDrive()).events())
            val incompatibleModel = existingCreatedTestModel(newModel.id(), "noscope", 360, version(2))
                .changeParam1("mlg")
            val incompatibleUpdate =
                UpdateModel(incompatibleModel, incompatibleModel.writeEvents(ModelEventDrive()).events())
            val merged = add.merge(incompatibleUpdate)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }

        When("Principal tries to merge AddModel change with UpdateModel change model with non successive events") {
            val add = AddModel(newModel, newModel.writeEvents(ModelEventDrive()).events())
            val incompatibleModel = existingCreatedTestModel(newModel.id(), "noscope", 360, V1).activate()
            val incompatibleUpdate =
                UpdateModel(incompatibleModel, incompatibleModel.writeEvents(ModelEventDrive()).events())
            val merged = add.merge(incompatibleUpdate)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }
    }
})
