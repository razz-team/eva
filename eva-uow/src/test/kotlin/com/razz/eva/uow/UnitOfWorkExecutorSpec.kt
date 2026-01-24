package com.razz.eva.uow

import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.EntityState.NewState.Companion.newState
import com.razz.eva.domain.Ration
import com.razz.eva.events.UowEvent
import com.razz.eva.persistence.PersistenceException.ModelRecordConstraintViolationException
import com.razz.eva.persistence.PersistenceException.StaleRecordException
import com.razz.eva.persistence.PersistenceException.UniqueModelRecordViolationException
import com.razz.eva.persistence.PrimaryConnectionRequiredFlag
import com.razz.eva.persistence.WithCtxConnectionTransactionManager
import com.razz.eva.repository.ModelRepos
import com.razz.eva.uow.BaseUnitOfWork.Configuration
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.CreateDepartmentUow.Params
import com.razz.eva.uow.Retry.StaleRecordFixedRetry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode.InstancePerLeaf
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.OpenTelemetry
import java.time.Duration.ofMillis
import java.time.Instant.ofEpochMilli
import java.util.*
import com.razz.eva.uow.composable.DummyUow
import kotlin.reflect.KClass
import java.time.InstantSource

class UnitOfWorkExecutorSpec : BehaviorSpec({

    isolationMode = InstancePerLeaf

    val clock = fixedUTC(ofEpochMilli(0))
    val departmentId = randomDepartmentId()
    val bossId = EmployeeId(UUID.randomUUID())
    val department = OwnedDepartment(
        id = departmentId,
        name = "KazahDepartment",
        headcount = 1,
        ration = Ration.BUBALEH,
        boss = bossId,
        entityState = newState(
            OwnedDepartmentCreated(
                departmentId = departmentId,
                name = "KazahDepartment",
                headcount = 1,
                ration = Ration.BUBALEH,
                boss = bossId,
            ),
        ),
    )

    Given("Params for UnitOfWork are defined") {
        val depId = DepartmentId(UUID.fromString("dd9a8b72-a473-4419-a49d-b718ea6e8e38"))
        val params = Params(
            boss = bossId,
            departmentName = "KazahDepartment",
            ration = Ration.BUBALEH,
        )

        And("Factory which reads connection context") {
            @Suppress("UNCHECKED_CAST") val factories = listOf(
                (DummyUow::class as KClass<DummyUow<String>>) withFactory {
                    object : DummyUow<String>(it) {
                        override suspend fun tryPerform(
                            principal: TestPrincipal,
                            params: Params,
                        ) = if (kotlin.coroutines.coroutineContext[PrimaryConnectionRequiredFlag] == null) {
                            noChanges("null")
                        } else {
                            noChanges("not null")
                        }
                    }
                },
            )
            val uowx = UnitOfWorkExecutor(
                factories,
                Persisting(WithCtxConnectionTransactionManager(), ModelRepos(), DummyEventRepository()),
                clock,
                OpenTelemetry.noop(),
            )

            When("UnitOfWorkExecutor executes Uow") {
                val observedContext = uowx
                    .execute((DummyUow::class as KClass<DummyUow<String>>), TestPrincipal) { DummyUow.Params }

                Then("Uow observed connection context") {
                    observedContext shouldBe "not null"
                }
            }
        }

        var tick = ofEpochMilli(0)
        val crawlingInstant = InstantSource {
            var tock = tick.plusMillis(1)
            tick = tock.plusMillis(1)
            tock
        }

        And("Factory which reads clock value") {
            val factories = listOf(
                (DummyUow::class as KClass<DummyUow<String>>) withFactory {
                    object : DummyUow<String>(it) {
                        override suspend fun tryPerform(
                            principal: TestPrincipal,
                            params: Params,
                        ): Changes<String> {
                            val i = this.clock.instant()
                            return noChanges(i.toEpochMilli().toString())
                        }
                    }
                },
            )

            When("UnitOfWorkExecutor created and frozen clock passed to persisting") {
                var execution = 0
                val eventRepo = DummyEventRepository { uowEvent ->
                    when (execution++) {
                        0 -> {
                            uowEvent.occurredAt shouldBe ofEpochMilli(1)
                            throw StaleRecordException(randomDepartmentId(), "department")
                        }
                        1 -> {
                            uowEvent.occurredAt shouldBe ofEpochMilli(3)
                        }
                        else -> throw IllegalStateException()
                    }
                }
                val txnManager = WithCtxConnectionTransactionManager()
                val uowx = UnitOfWorkExecutor(
                    factories,
                    Persisting(txnManager, ModelRepos(), eventRepo),
                    crawlingInstant,
                    OpenTelemetry.noop(),
                )

                Then("Clock property wasn't called") {
                    tick shouldBe ofEpochMilli(0)
                }

                And("UnitOfWorkExecutor executes Uow") {
                    val observedMilli = uowx
                        .execute((DummyUow::class as KClass<DummyUow<String>>), TestPrincipal) { DummyUow.Params }

                    Then("Uow observed frozen clock twice") {
                        observedMilli shouldBe "3"
                    }

                    And("Clock fabric was called two times due to retries") {
                        tick shouldBe ofEpochMilli(4)
                    }
                }
            }
        }

        And("Ad hoc factory") {
            val factory = { exCtx: ExecutionContext ->
                object : DummyUow<String>(exCtx) {
                    override suspend fun tryPerform(
                        principal: TestPrincipal,
                        params: Params,
                    ) = noChanges("Success")
                }
            }

            When("UnitOfWorkExecutor created without factories") {
                val uowx = UnitOfWorkExecutor(
                    listOf(),
                    Persisting(WithCtxConnectionTransactionManager(), ModelRepos(), DummyEventRepository()),
                    clock,
                    OpenTelemetry.noop(),
                )

                And("UnitOfWorkExecutor executes Uow") {
                    val result = uowx.execute(TestPrincipal, factory) { DummyUow.Params }

                    Then("Result should be Success") {
                        result shouldBe "Success"
                    }
                }
            }
        }

        And("Two ClassToUow with the same key") {
            val factories = listOf(
                CreateDepartmentUow::class withFactory { mockk() },
                CreateDepartmentUow::class withFactory { mockk() },
            )

            When("Principal creates UnitOfWorkExecutor") {
                val attempt = {
                    UnitOfWorkExecutor(factories, mockk(), clock, OpenTelemetry.noop())
                }

                Then("Exception is thrown") {
                    val ex = shouldThrow<IllegalArgumentException> { attempt() }
                    ex.message shouldBe "Attempted to register multiple factories for CreateDepartmentUow"
                }
            }
        }

        And("UnitOfWorkExecutor and UnitOfWork are configured") {
            val anotherId = randomDepartmentId()
            val anotherModel = OwnedDepartment(
                id = anotherId,
                name = "LondonDepartment",
                headcount = 1,
                ration = Ration.BUBALEH,
                boss = bossId,
                entityState = newState(
                    OwnedDepartmentCreated(
                        departmentId = anotherId,
                        name = "LondonDepartment",
                        headcount = 1,
                        ration = Ration.BUBALEH,
                        boss = bossId,
                    ),
                ),
            )
            val unitOfWork = mockk<CreateDepartmentUow>()
            val rawUnitOfWork = unitOfWork as UnitOfWork<TestPrincipal, Params, OwnedDepartment>
            val persisting = mockk<Persisting>(relaxed = true)

            every { unitOfWork.name() } returns "MockOfCreateDepartmentUow"
            every { rawUnitOfWork.configuration().supportsOutOfOrderPersisting } returns true

            val uowx = UnitOfWorkExecutor(
                persisting = persisting,
                factories = listOf(
                    CreateDepartmentUow::class withFactory { unitOfWork },
                ),
                clock = clock,
                openTelemetry = OpenTelemetry.noop(),
            )

            And("UnitOfWork has one retry and returns result") {
                coEvery {
                    persisting.persist(
                        uowName = "MockOfCreateDepartmentUow",
                        params = params,
                        principal = TestPrincipal,
                        changes = listOf(),
                        now = clock.instant(),
                        uowSupportsOutOfOrderPersisting = true,
                    )
                } returns Pair(UowEvent.Id.random(), listOf(anotherModel, department))
                every { rawUnitOfWork.configuration() } returns
                    Configuration(StaleRecordFixedRetry(1, ofMillis(100)), true)
                val changes = RealisedChanges(department, listOf())
                coEvery {
                    rawUnitOfWork.tryPerform(TestPrincipal, eq(params))
                } returns changes

                When("Principal executes UnitOfWork") {
                    val createdDepartment = uowx.execute(CreateDepartmentUow::class, TestPrincipal) { params }

                    Then("Correct result will be returned") {
                        createdDepartment shouldBe department
                    }

                    And("Changes are persisted") {
                        coVerify {
                            persisting.persist(
                                uowName = "MockOfCreateDepartmentUow",
                                params = params,
                                principal = TestPrincipal,
                                changes = changes.toPersist,
                                now = clock.instant(),
                                uowSupportsOutOfOrderPersisting = true,
                            )
                        }
                    }
                }
            }

            And("UnitOfWork throws some user exception") {
                coEvery {
                    rawUnitOfWork.tryPerform(TestPrincipal, eq(params))
                } throws IllegalStateException("User exception")

                When("Principal executes UnitOfWork") {
                    val attempt = suspend {
                        uowx.execute(CreateDepartmentUow::class, TestPrincipal) { params }
                    }

                    Then("Exception is returned") {
                        val ex = shouldThrow<IllegalStateException> { attempt() }
                        ex.message shouldBe "User exception"
                    }

                    And("No changes are persisted") {
                        verify { persisting wasNot Called }
                    }
                }
            }

            And("UnitOfWork has one retry and persisting saves result on second attempt") {
                val retry = mockk<StaleRecordFixedRetry>()
                val ex = StaleRecordException(depId, "department")
                every { rawUnitOfWork.configuration() } returns Configuration(retry, true)
                every { retry.getNextDelay(eq(0), eq(ex)) } returns ofMillis(0)
                coEvery {
                    rawUnitOfWork.tryPerform(TestPrincipal, eq(params))
                } returns RealisedChanges(department, listOf())
                coEvery {
                    persisting.persist(
                        uowName = "MockOfCreateDepartmentUow",
                        params = params,
                        principal = TestPrincipal,
                        changes = listOf(),
                        now = clock.instant(),
                        uowSupportsOutOfOrderPersisting = true,
                    )
                } throws ex andThen Pair(UowEvent.Id.random(), listOf(department))
                coEvery { rawUnitOfWork.onFailure(eq(params), eq(ex)) } throws ex

                When("Principal executes UnitOfWork") {
                    val createdDepartment = uowx.execute(CreateDepartmentUow::class, TestPrincipal) { params }

                    Then("Correct result will be returned") {
                        createdDepartment shouldBe department
                        coVerify(exactly = 1) { retry.getNextDelay(eq(0), eq(ex)) }
                    }
                }
            }

            And("UnitOfWork has one retry and returns onFailure result on second attempt") {
                val retry = mockk<StaleRecordFixedRetry>()
                val ex = StaleRecordException(depId, "department")
                every { rawUnitOfWork.configuration() } returns Configuration(retry, true)
                every { retry.getNextDelay(eq(0), eq(ex)) } returns ofMillis(0)
                every { retry.getNextDelay(eq(1), eq(ex)) } returns null
                coEvery {
                    rawUnitOfWork.tryPerform(TestPrincipal, eq(params))
                } returns RealisedChanges(department, listOf())
                coEvery {
                    persisting.persist(
                        "MockOfCreateDepartmentUow",
                        eq(params),
                        TestPrincipal,
                        listOf(),
                        ofEpochMilli(0),
                        true,
                    )
                } throws ex andThenThrows ex
                coEvery { rawUnitOfWork.onFailure(eq(params), eq(ex)) } returns department

                When("Principal executes UnitOfWork") {
                    val createdDepartment = uowx.execute(CreateDepartmentUow::class, TestPrincipal) { params }

                    Then("Correct result will be returned") {
                        createdDepartment shouldBe department
                        coVerify(exactly = 1) { retry.getNextDelay(0, ex) }
                        coVerify(exactly = 1) { retry.getNextDelay(1, ex) }
                    }
                }
            }

            And("UnitOfWork has one retry and Persisting throws StaleRecordException constantly") {
                val ex = StaleRecordException(depId, "department")
                every { rawUnitOfWork.configuration() } returns Configuration.default()
                coEvery {
                    rawUnitOfWork.tryPerform(TestPrincipal, eq(params))
                } returns RealisedChanges(department, listOf())
                coEvery {
                    persisting.persist(
                        "MockOfCreateDepartmentUow",
                        eq(params),
                        TestPrincipal,
                        listOf(),
                        ofEpochMilli(0),
                        false,
                    )
                } throws ex
                coEvery { rawUnitOfWork.onFailure(eq(params), eq(ex)) } throws StaleRecordException(depId, "department")

                When("Principal executes UnitOfWork") {
                    val execution = suspend {
                        uowx.execute(CreateDepartmentUow::class, TestPrincipal) { params }
                    }

                    Then("Exception will be thrown") {
                        shouldThrow<StaleRecordException> { execution() }
                    }
                }
            }

            And(
                "UnitOfWork has one retry, has custom exception mapping " +
                    "and Persisting throws StaleRecordException constantly",
            ) {
                val ex = StaleRecordException(depId, "department")
                every { rawUnitOfWork.configuration() } returns Configuration.default()
                coEvery {
                    rawUnitOfWork.tryPerform(TestPrincipal, eq(params))
                } returns RealisedChanges(department, listOf())
                coEvery {
                    persisting.persist(
                        "MockOfCreateDepartmentUow",
                        eq(params),
                        TestPrincipal,
                        listOf(),
                        ofEpochMilli(0),
                        false,
                    )
                } throws ex
                coEvery {
                    rawUnitOfWork.onFailure(eq(params), eq(ex))
                } throws IllegalStateException("${depId.id} priunil")

                When("Principal executes UnitOfWork") {
                    val execution = suspend {
                        uowx.execute(CreateDepartmentUow::class, TestPrincipal) { params }
                    }

                    Then("Exception will be thrown") {
                        val isex = shouldThrow<IllegalStateException> { execution() }
                        isex.message shouldBe "${depId.id} priunil"
                    }
                }
            }

            And("Persisting throws UniqueModelRecordViolationException constantly") {
                val ex = UniqueModelRecordViolationException(depId, "DEPARTMENTS", "легендарный_певец_Бока_idx")
                every { rawUnitOfWork.configuration() } returns Configuration.default()
                coEvery {
                    rawUnitOfWork.tryPerform(TestPrincipal, eq(params))
                } returns RealisedChanges(department, listOf())
                coEvery {
                    persisting.persist(
                        "MockOfCreateDepartmentUow",
                        eq(params),
                        TestPrincipal,
                        listOf(),
                        ofEpochMilli(0),
                        false,
                    )
                } throws ex
                coEvery { rawUnitOfWork.onFailure(eq(params), eq(ex)) } throws ex

                When("Principal executes UnitOfWork") {
                    val execution = suspend {
                        uowx.execute(CreateDepartmentUow::class, TestPrincipal) { params }
                    }

                    Then("Exception will be thrown") {
                        shouldThrow<UniqueModelRecordViolationException> { execution() }
                    }
                }
            }

            And("Persisting throws ModelRecordConstraintViolationException constantly") {
                val ex = ModelRecordConstraintViolationException(depId, "DEPARTMENTS", "популярный_певец_Жока_idx")
                every { rawUnitOfWork.configuration() } returns Configuration.default()
                coEvery {
                    rawUnitOfWork.tryPerform(TestPrincipal, eq(params))
                } returns RealisedChanges(department, listOf())
                coEvery {
                    persisting.persist(
                        "MockOfCreateDepartmentUow",
                        eq(params),
                        TestPrincipal,
                        listOf(),
                        ofEpochMilli(0),
                        false,
                    )
                } throws ex
                coEvery { rawUnitOfWork.onFailure(eq(params), eq(ex)) } throws ex

                When("Principal executes UnitOfWork") {
                    val execution = suspend {
                        uowx.execute(CreateDepartmentUow::class, TestPrincipal) { params }
                    }

                    Then("Exception will be thrown") {
                        shouldThrow<ModelRecordConstraintViolationException> { execution() }
                    }
                }
            }

            And("Persisting throws UniqueModelRecordViolationException and UnitOfWork return result on failure") {
                val resultModel = mockk<OwnedDepartment>()
                val ex = UniqueModelRecordViolationException(depId, "DEPARTMENTS", "легендарный_певец_Бока_idx")
                every { rawUnitOfWork.configuration() } returns Configuration.default()
                coEvery {
                    rawUnitOfWork.tryPerform(TestPrincipal, eq(params))
                } returns RealisedChanges(department, listOf())
                coEvery {
                    persisting.persist(
                        "MockOfCreateDepartmentUow",
                        eq(params),
                        TestPrincipal,
                        listOf(),
                        ofEpochMilli(0),
                        false,
                    )
                } throws ex
                coEvery { rawUnitOfWork.onFailure(eq(params), eq(ex)) } returns resultModel

                When("Principal executes UnitOfWork") {
                    val result = uowx.execute(CreateDepartmentUow::class, TestPrincipal) { params }

                    Then("Result is correct") {
                        result shouldBe resultModel
                    }
                }
            }
        }
    }
})
