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

class ChangeSpec : BehaviorSpec({

    val persisting = object : ModelPersisting {
        override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> add(model: M) = Unit
        override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> update(model: M) = Unit
    }

    Given("Model in PersistentState") {
        val persistedModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        When("Principal tries to UPDATE persisted model via Change") {
            val change = Update(persistedModel, emptyList())
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register unchanged model [${persistedModel.id()}] as changed"
            }
        }

        When("Principal tries to ADD persisted model via Change") {
            val change = Add(persistedModel, emptyList())
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register unchanged model [${persistedModel.id()}] as new"
            }
        }

        When("Principal tries to merge Noop change with Add change") {
            val noop = Noop(persistedModel)
            val newModel = createdTestModel("noscope", 360).activate()
            val events = newModel.writeEvents(ModelEventDrive()).events()
            val add = Add(newModel, events)
            val merged = noop.merge(add)

            Then("Merged change is Add") {
                merged shouldBe add
            }
        }

        When("Principal tries to merge Noop change with Update change") {
            val noop = Noop(persistedModel)
            val dirtyModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1).activate()
            val events = dirtyModel.writeEvents(ModelEventDrive()).events()
            val update = Update(dirtyModel, events)
            val merged = noop.merge(update)

            Then("Merged change is Update") {
                merged shouldBe update
            }
        }

        When("Principal tries to merge Noop change with Noop change") {
            val noop = Noop(persistedModel)
            val anotherNoop = Noop(persistedModel)
            val merged = noop.merge(anotherNoop)

            Then("Merged change is another Noop") {
                merged shouldBe anotherNoop
            }
        }
    }

    Given("Model in DirtyState") {
        val dirtyModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
            .changeParam1("mlg")

        When("Principal tries to ADD dirty model via Change") {
            val change = Add(dirtyModel, emptyList())
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register changed model [${dirtyModel.id()}] as new"
            }
        }

        When("Principal tries mark dirty model as not changed via Change") {
            val change = Noop(dirtyModel)
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register changed model [${dirtyModel.id()}] as unchanged"
            }
        }

        When("Principal tries to merge Update change with Add change") {
            val update = Update(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            val newModel = createdTestModel("noscope", 360).activate()
            val add = Add(newModel, newModel.writeEvents(ModelEventDrive()).events())
            val merged = update.merge(add)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }

        When("Principal tries to merge Update change with Noop change") {
            val update = Update(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            val persistedModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
            val noop = Noop(persistedModel)
            val merged = update.merge(noop)

            Then("Merged change is null") {
                merged shouldBe update
            }
        }

        When("Principal tries to merge Update change with Update change") {
            val update = Update(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            val updatedModel = dirtyModel.activate()
            val anotherUpdate = Update(updatedModel, updatedModel.writeEvents(ModelEventDrive()).events())
            val merged = update.merge(anotherUpdate)

            Then("Merged change is Update") {
                merged shouldBe anotherUpdate
            }
        }

        When("Principal tries to merge Update change with Update change for different model") {
            val update = Update(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            val incompatibleModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
                .changeParam1("mlg")
            val incompatibleUpdate =
                Update(incompatibleModel, incompatibleModel.writeEvents(ModelEventDrive()).events())
            val merged = update.merge(incompatibleUpdate)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }

        When("Principal tries to merge Update change with Update change model of different version") {
            val update = Update(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            val incompatibleModel = existingCreatedTestModel(dirtyModel.id(), "noscope", 360, version(2))
                .changeParam1("mlg")
            val incompatibleUpdate =
                Update(incompatibleModel, incompatibleModel.writeEvents(ModelEventDrive()).events())
            val merged = update.merge(incompatibleUpdate)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }

        When("Principal tries to merge Update change with Update change model with non successive events") {
            val update = Update(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            val incompatibleModel = existingCreatedTestModel(dirtyModel.id(), "noscope", 360, V1).activate()
            val incompatibleUpdate =
                Update(incompatibleModel, incompatibleModel.writeEvents(ModelEventDrive()).events())
            val merged = update.merge(incompatibleUpdate)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }
    }

    Given("Model in NewState") {
        val newModel = createdTestModel("noscope", 360).changeParam1("mlg")

        When("Principal tries to UPDATE new model via Change") {
            val change = Update(newModel, emptyList())
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register new model [${newModel.id()}] as changed"
            }
        }

        When("Principal tries mark new model as not changed via Change") {
            val change = Noop(newModel)
            val attempt = { change.persist(persisting) }

            Then("IllegalArgumentException thrown") {
                val ex = shouldThrow<IllegalArgumentException>(attempt)
                ex.message shouldBe "Attempted to register new model [${newModel.id()}] as unchanged"
            }
        }

        When("Principal tries to merge Add change with Add change") {
            val add = Add(newModel, newModel.writeEvents(ModelEventDrive()).events())
            val anotherNewModel = createdTestModel("noscope", 360).activate()
            val anotherAdd = Add(anotherNewModel, anotherNewModel.writeEvents(ModelEventDrive()).events())
            val merged = add.merge(anotherAdd)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }

        When("Principal tries to merge Add change with Noop change") {
            val add = Add(newModel, newModel.writeEvents(ModelEventDrive()).events())
            val persistedModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
            val noop = Noop(persistedModel)
            val merged = add.merge(noop)

            Then("Merged change is Add") {
                merged shouldBe add
            }
        }

        When("Principal tries to merge Add change with new model wrapped in Update change") {
            val add = Add(newModel, newModel.writeEvents(ModelEventDrive()).events())
            val dirtyModel = newModel.changeParam1("mlg").activate()
            val update = Update(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            val merged = add.merge(update)

            Then("Merged change is Update") {
                merged shouldBe Add(dirtyModel, dirtyModel.writeEvents(ModelEventDrive()).events())
            }
        }

        When("Principal tries to merge Add change with Update change for different model") {
            val add = Add(newModel, newModel.writeEvents(ModelEventDrive()).events())
            val incompatibleModel = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
                .changeParam1("mlg")
            val incompatibleUpdate =
                Update(incompatibleModel, incompatibleModel.writeEvents(ModelEventDrive()).events())
            val merged = add.merge(incompatibleUpdate)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }

        When("Principal tries to merge Add change with Update change model of different version") {
            val add = Add(newModel, newModel.writeEvents(ModelEventDrive()).events())
            val incompatibleModel = existingCreatedTestModel(newModel.id(), "noscope", 360, version(2))
                .changeParam1("mlg")
            val incompatibleUpdate =
                Update(incompatibleModel, incompatibleModel.writeEvents(ModelEventDrive()).events())
            val merged = add.merge(incompatibleUpdate)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }

        When("Principal tries to merge Add change with Update change model with non successive events") {
            val add = Add(newModel, newModel.writeEvents(ModelEventDrive()).events())
            val incompatibleModel = existingCreatedTestModel(newModel.id(), "noscope", 360, V1).activate()
            val incompatibleUpdate =
                Update(incompatibleModel, incompatibleModel.writeEvents(ModelEventDrive()).events())
            val merged = add.merge(incompatibleUpdate)

            Then("Merged change is null") {
                merged shouldBe null
            }
        }
    }
})
