package com.razz.eva.uow

import com.razz.eva.domain.TestModel
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.TestModelEvent.TestModelCreated
import com.razz.eva.domain.TestModelEvent.TestModelStatusChanged
import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
import com.razz.eva.domain.TestModelStatus.ACTIVE
import com.razz.eva.domain.TestModelStatus.CREATED
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.uow.UnitOfWork.Configuration.Companion.withAllowedEmptyChanges
import com.razz.eva.uow.params.UowParams
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.tracing.Tracing.noopTracer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import java.time.Clock

internal abstract class DummyUow(clock: Clock) : UnitOfWork<TestPrincipal, DummyUow.Params, String>(clock) {
    @Serializable
    object Params : UowParams<Params> {
        override fun serialization() = serializer()
    }
}

internal abstract class DummyNoChangesUow(
    clock: Clock
) : UnitOfWork<TestPrincipal, DummyNoChangesUow.Params, String>(clock, withAllowedEmptyChanges()) {
    @Serializable
    object Params : UowParams<Params> {
        override fun serialization() = serializer()
    }
}

class ChangesDslSpec : FunSpec({

    val now = millisUTC().instant()
    val clock = fixedUTC(now)

    test("Changes dsl should return properly built ChangesWithResult after execution by uow") {
        val model0 = createdTestModel("MLG", 420).activate()
        val model1 = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)
            .activate()

        val uow = object : DummyUow(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params): ChangesWithResult<String> {
                val changes = changes {
                    add(model0)
                    update(model1)
                    "K P A C U B O"
                }

                changes.toPersist shouldBe listOf(
                    Add(
                        model0,
                        listOf(
                            TestModelCreated(model0.id()),
                            TestModelStatusChanged(model0.id(), CREATED, ACTIVE)
                        )
                    ),
                    Update(model1, listOf(TestModelStatusChanged(model1.id(), CREATED, ACTIVE)))
                )
                changes.result shouldBe "K P A C U B O"

                return changes
            }
        }
        UnitOfWorkExecutor(
            listOf(DummyUow::class withFactory { uow }),
            FakeMemorizingPersisting(TestModel::class),
            tracer = noopTracer()
        ).execute(DummyUow::class, TestPrincipal) { DummyUow.Params }
    }

    test(
        "Changes dsl should return properly built ChangesWithResult after execution by uow " +
            "with changes when allowedEmptyChanges flag is set"
    ) {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1).activate()

        val uow = object : DummyNoChangesUow(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params): ChangesWithResult<String> {
                val changes = changes {
                    update(model)
                    "K P A C U B O"
                }

                changes.toPersist shouldBe listOf(
                    Update(model, listOf(TestModelStatusChanged(model.id(), CREATED, ACTIVE)))
                )
                changes.result shouldBe "K P A C U B O"

                return changes
            }
        }
        UnitOfWorkExecutor(
            listOf(DummyNoChangesUow::class withFactory { uow }),
            FakeMemorizingPersisting(TestModel::class),
            tracer = noopTracer()
        ).execute(DummyNoChangesUow::class, TestPrincipal) { DummyNoChangesUow.Params }
    }

    test(
        "Changes dsl should return properly built ChangesWithResult after execution by uow " +
            "without changes when allowedEmptyChanges flag is set"
    ) {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyNoChangesUow(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params): ChangesWithResult<String> {
                val changes = changes {
                    update(model)
                    "K P A C U B O"
                }

                changes.toPersist shouldBe emptyList()
                changes.result shouldBe "K P A C U B O"

                return changes
            }
        }
        UnitOfWorkExecutor(
            listOf(DummyNoChangesUow::class withFactory { uow }),
            FakeMemorizingPersisting(TestModel::class),
            tracer = noopTracer()
        ).execute(DummyNoChangesUow::class, TestPrincipal) { DummyNoChangesUow.Params }
    }

    test(
        "Changes dsl should throw after execution by uow " +
            "without changes when allowedEmptyChanges flag is not set"
    ) {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyUow(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                update(model)
                "K P A C U B O"
            }
        }
        val exception = shouldThrow<IllegalArgumentException> {
            UnitOfWorkExecutor(
                listOf(DummyUow::class withFactory { uow }),
                FakeMemorizingPersisting(TestModel::class),
                tracer = noopTracer()
            ).execute(DummyUow::class, TestPrincipal) { DummyUow.Params }
        }
        exception.message shouldBe "Attempted to register unchanged model [${model.id()}]" +
            " but empty changes were disallowed"
    }

    test("Changes dsl should throw when no models were added or updated") {

        val uow = object : DummyUow(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                "K P A C U B O"
            }
        }

        val exception = shouldThrow<IllegalStateException> {
            UnitOfWorkExecutor(
                listOf(DummyUow::class withFactory { uow }),
                FakeMemorizingPersisting(TestModel::class),
                tracer = noopTracer()
            ).execute(DummyUow::class, TestPrincipal) { DummyUow.Params }
        }
        exception.message shouldBe "No changes to persist"
    }

    test(
        "Changes dsl should return properly built ChangesWithResult after execution by uow " +
            "without changes when allowedEmptyChanges flag was not set and notChanged:M was called"
    ) {
        val model = existingCreatedTestModel(randomTestModelId(), "noscope", 360, V1)

        val uow = object : DummyUow(clock) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params): ChangesWithResult<String> {
                val changes = changes {
                    notChanged(model)
                    "K P A C U B O"
                }

                changes.toPersist shouldBe listOf(Noop)
                changes.result shouldBe "K P A C U B O"

                return changes
            }
        }
        UnitOfWorkExecutor(
            listOf(DummyUow::class withFactory { uow }),
            FakeMemorizingPersisting(TestModel::class),
            tracer = noopTracer()
        ).execute(DummyUow::class, TestPrincipal) { DummyUow.Params }
    }
})
