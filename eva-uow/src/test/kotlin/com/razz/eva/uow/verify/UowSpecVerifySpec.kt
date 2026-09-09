package com.razz.eva.uow.verify

import com.razz.eva.domain.TestModel
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.TestModelEvent.TestModelCreated
import com.razz.eva.domain.TestModelEvent.TestModelStatusChanged
import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.composable.DummyUow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.opentelemetry.api.OpenTelemetry
import com.razz.eva.uow.composable.UnitOfWork as ComposableUnitOfWork

class UowSpecVerifySpec : BehaviorSpec({

    val executionContext = ExecutionContext(fixedUTC(millisUTC().instant()), OpenTelemetry.noop())

    val equalityAware = object : EqualityVerifierAware {
        override val equalityVerifier = object : EqualityVerifier {
            override fun <T> verify(expected: T, actual: T) {
                expected shouldBe actual
            }
        }
    }

    Given("A change adding a new model, returned by the unit of work") {
        val added = createdTestModel("MLG", 420)
        val changes = ChangesAccumulator().withAddedModel(added).withResult(added)

        When("addsAndReturns verifies with a block that counts its own invocations") {
            var invocations = 0
            changes verifyInOrder {
                addsAndReturns<TestModel> { invocations++ }
                emits<TestModelCreated> { }
            }

            Then("The block ran exactly once") {
                invocations shouldBe 1
            }
        }

        When("addsAndReturns is given an id and a block that counts its own invocations") {
            var invocations = 0
            with(equalityAware) {
                changes verifyInOrder {
                    addsAndReturns<TestModel>(added.id()) { invocations++ }
                    emits<TestModelCreated> { }
                }
            }

            Then("The block ran exactly once") {
                invocations shouldBe 1
            }
        }

        When("The verify block nests emits and the change raises a single event") {
            val verifying = {
                changes verifyInOrder {
                    addsAndReturns<TestModel> {
                        emits<TestModelCreated> {
                            testModelId shouldBe added.id()
                        }
                    }
                }
            }

            Then("Nested emits consumes the event and the spec ends clean") {
                verifying()
            }
        }

        When("The verify block does not hold") {
            val verifying = {
                changes verifyInOrder {
                    addsAndReturns<TestModel> { status() shouldBe null }
                    emits<TestModelCreated> { }
                }
            }

            Then("The block failure surfaces") {
                shouldThrow<AssertionError>(verifying)
            }
        }

        When("addsAndReturns is given an id that does not match") {
            val verifying = {
                with(equalityAware) {
                    changes verifyInOrder {
                        addsAndReturns<TestModel>(randomTestModelId()) { }
                        emits<TestModelCreated> { }
                    }
                }
            }

            Then("The id mismatch surfaces") {
                shouldThrow<AssertionError>(verifying)
            }
        }
    }

    Given("A change raising two events, verified with a single nested emits") {
        val added = createdTestModel("MLG", 420).activate()
        val changes = ChangesAccumulator().withAddedModel(added).withResult(added)

        When("The verify block asserts only the first of the two events") {
            val verifying = {
                changes verifyInOrder {
                    addsAndReturns<TestModel> { emits<TestModelCreated> { } }
                }
            }

            Then("The unasserted event is reported, naming it and the one-emits-per-event rule") {
                val exception = shouldThrow<IllegalStateException>(verifying)
                exception.message shouldContain "No more events expected"
                exception.message shouldContain "TestModelStatusChanged"
                exception.message shouldContain "its own emits call"
            }
        }

        When("The verify block asserts both events") {
            val verifying = {
                changes verifyInOrder {
                    addsAndReturns<TestModel> {
                        emits<TestModelCreated> { }
                        emits<TestModelStatusChanged> { }
                    }
                }
            }

            Then("The spec ends clean") {
                verifying()
            }
        }
    }

    Given("A change updating a model, returned by the unit of work") {
        val updated = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1).activate()
        val changes = ChangesAccumulator().withUpdatedModel(updated).withResult(updated)

        When("updatesAndReturns verifies with a block that counts its own invocations") {
            var invocations = 0
            changes verifyInOrder {
                updatesAndReturns<TestModel> { invocations++ }
                emits<TestModelStatusChanged> { }
            }

            Then("The block ran exactly once") {
                invocations shouldBe 1
            }
        }

        When("updatesAndReturns is given an id and a block that counts its own invocations") {
            var invocations = 0
            with(equalityAware) {
                changes verifyInOrder {
                    updatesAndReturns<TestModel>(updated.id()) { invocations++ }
                    emits<TestModelStatusChanged> { }
                }
            }

            Then("The block ran exactly once") {
                invocations shouldBe 1
            }
        }

        When("The verify block nests emits and the change raises a single event") {
            val verifying = {
                changes verifyInOrder {
                    updatesAndReturns<TestModel> {
                        emits<TestModelStatusChanged> {
                            testModelId shouldBe updated.id()
                        }
                    }
                }
            }

            Then("Nested emits consumes the event and the spec ends clean") {
                verifying()
            }
        }

        When("The verify block does not hold") {
            val verifying = {
                changes verifyInOrder {
                    updatesAndReturns<TestModel> { status() shouldBe null }
                    emits<TestModelStatusChanged> { }
                }
            }

            Then("The block failure surfaces") {
                shouldThrow<AssertionError>(verifying)
            }
        }

        When("updatesAndReturns is given an id that does not match") {
            val verifying = {
                with(equalityAware) {
                    changes verifyInOrder {
                        updatesAndReturns<TestModel>(randomTestModelId()) { }
                        emits<TestModelStatusChanged> { }
                    }
                }
            }

            Then("The id mismatch surfaces") {
                shouldThrow<AssertionError>(verifying)
            }
        }
    }

    Given("A composed unit of work whose parent merges a newer instance over the child's change") {
        val seed = createdTestModel("MLG", 420)
        val child = { exCtx: ExecutionContext ->
            object : ComposableUnitOfWork<TestPrincipal, DummyUow.Params, TestModel>(exCtx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) = changes {
                    add(seed)
                }
            }
        }

        When("The parent returns the instance the child registered, superseded by the merge") {
            val parent = object : DummyUow<TestModel>(executionContext) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                    val added = execute(child, principal) { DummyUow.Params }
                    update((added as TestModel.CreatedTestModel).activate())
                    added
                }
            }

            Then("The result is accepted: same id, and the change carries the result's events") {
                val changes = parent.tryPerform(TestPrincipal, DummyUow.Params)
                changes verifyInOrder {
                    addsAndReturns<TestModel> { id() shouldBe seed.id() }
                    emits<TestModelCreated> { }
                    emits<TestModelStatusChanged> { }
                }
            }
        }

        When("The parent seeds its result with roundtrip before the merge") {
            val parent = object : DummyUow<TestModel>(executionContext) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                    val added = execute(child, principal) { DummyUow.Params }
                    val roundtripped = roundtrip { p -> p(added) }
                    update((added as TestModel.CreatedTestModel).activate())
                    roundtripped
                }
            }

            Then("The stale roundtrip seed is accepted") {
                val changes = parent.tryPerform(TestPrincipal, DummyUow.Params)
                changes verifyInOrder {
                    addsAndReturns<TestModel> { id() shouldBe seed.id() }
                    emits<TestModelCreated> { }
                    emits<TestModelStatusChanged> { }
                }
            }
        }
    }

    Given("A unit of work adding a model and returning it through roundtrip") {
        val added = createdTestModel("MLG", 420)
        val uow = object : DummyUow<TestModel>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                add(added)
                roundtrip { p -> p(added) }
            }
        }

        When("addsAndReturns verifies the change") {
            Then("The roundtrip seed is accepted") {
                val changes = uow.tryPerform(TestPrincipal, DummyUow.Params)
                changes verifyInOrder {
                    addsAndReturns<TestModel> { id() shouldBe added.id() }
                    emits<TestModelCreated> { }
                }
            }
        }
    }

    Given("A change whose result carries a mutation that was never registered") {
        val added = createdTestModel("MLG", 420)
        val changes = ChangesAccumulator()
            .withAddedModel(added)
            .withResult(added.changeParam1("NOT REGISTERED"))

        When("addsAndReturns verifies the change") {
            val verifying = {
                changes verifyInOrder {
                    addsAndReturns<TestModel> { }
                    emits<TestModelCreated> { }
                }
            }

            Then("The unregistered events are named on both sides") {
                val exception = shouldThrow<IllegalStateException>(verifying)
                exception.message shouldContain "carries events the change does not"
                exception.message shouldContain added.id().stringValue()
                exception.message shouldContain "The change holds [TestModelCreated]"
                exception.message shouldContain "the result holds [TestModelCreated, TestModelEvent1]"
            }
        }
    }

    Given("A change whose result has the same id but a different history") {
        val id = randomTestModelId()
        val updated = existingCreatedTestModel(id, "noscope", 360, V1).activate()
        val divergent = existingCreatedTestModel(id, "noscope", 360, V1).activate()
        val changes = ChangesAccumulator().withUpdatedModel(updated).withResult(divergent)

        When("updatesAndReturns verifies the change") {
            val verifying = {
                changes verifyInOrder {
                    updatesAndReturns<TestModel> { }
                    emits<TestModelStatusChanged> { }
                }
            }

            Then("The divergent history is reported") {
                val exception = shouldThrow<IllegalStateException>(verifying)
                exception.message shouldContain "carries a different history"
                exception.message shouldContain id.stringValue()
            }
        }
    }

    Given("A change whose result is an unrelated model of the same type") {
        val added = createdTestModel("MLG", 420)
        // persisted, not new: an unregistered NEW model in a result is refused by withResult itself,
        // so the id mismatch this pins would never reach the verify DSL
        val unrelated = existingCreatedTestModel(randomTestModelId(), "MLG", 420, V1)
        val changes = ChangesAccumulator().withAddedModel(added).withResult(unrelated)

        When("addsAndReturns verifies the change") {
            val verifying = {
                changes verifyInOrder {
                    addsAndReturns<TestModel> { }
                    emits<TestModelCreated> { }
                }
            }

            Then("Both ids and states are reported, pointing at verify order") {
                val exception = shouldThrow<IllegalStateException>(verifying)
                exception.message shouldContain "the ids differ"
                exception.message shouldContain added.id().stringValue()
                exception.message shouldContain unrelated.id().stringValue()
                exception.message shouldContain "CreatedTestModel[id = "
                exception.message shouldContain "new, version = 0, events = [TestModelCreated]"
                exception.message shouldContain "same order as the registered changes"
            }
        }
    }

    Given("A change whose result is null") {
        val added = createdTestModel("MLG", 420)
        val changes = ChangesAccumulator().withAddedModel(added).withResult<Any?>(null)

        When("addsAndReturns verifies the change") {
            val verifying = {
                changes verifyInOrder {
                    addsAndReturns<TestModel> { }
                    emits<TestModelCreated> { }
                }
            }

            Then("The result is reported as not being a model at all") {
                val exception = shouldThrow<IllegalStateException>(verifying)
                exception.message shouldContain "Result is not a model at all"
                exception.message shouldContain added.id().stringValue()
                exception.message shouldContain "Result was [null]"
            }
        }
    }

    Given("A change whose result is neither a model nor of the right id") {
        val added = createdTestModel("MLG", 420)
        val changes = ChangesAccumulator().withAddedModel(added).withResult(added.id())

        When("addsAndReturns is given a mismatching id as well") {
            val verifying = {
                with(equalityAware) {
                    changes verifyInOrder {
                        addsAndReturns<TestModel>(randomTestModelId()) { }
                        emits<TestModelCreated> { }
                    }
                }
            }

            Then("The id check runs first, so the id mismatch is what surfaces") {
                shouldThrow<AssertionError>(verifying)
            }
        }

        When("addsAndReturns is given the matching id") {
            val verifying = {
                with(equalityAware) {
                    changes verifyInOrder {
                        addsAndReturns<TestModel>(added.id()) { }
                        emits<TestModelCreated> { }
                    }
                }
            }

            Then("The result is reported as not being a model at all, naming its type") {
                val exception = shouldThrow<IllegalStateException>(verifying)
                exception.message shouldContain "Result is not a model at all"
                exception.message shouldContain "Result was [TestModelId:"
            }
        }
    }
})
