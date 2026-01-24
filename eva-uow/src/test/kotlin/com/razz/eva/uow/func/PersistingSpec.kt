package com.razz.eva.uow.func

import com.razz.eva.domain.Department
import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent.BossChanged
import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeEvent.DepartmentChanged
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.NewState.Companion.newState
import com.razz.eva.domain.ModelState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.events.EventPublisher
import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import com.razz.eva.persistence.DummyConnection
import com.razz.eva.persistence.WithCtxConnectionTransactionManager
import com.razz.eva.repository.EventRepository
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.ModelRepository
import com.razz.eva.repository.TransactionalContext.Companion.transactionalContext
import com.razz.eva.repository.hasRepo
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.uow.ExecutionStep
import com.razz.eva.uow.ExecutionStep.ModelAdded
import com.razz.eva.uow.ExecutionStep.ModelUpdated
import com.razz.eva.uow.ExecutionStep.ModelsAdded
import com.razz.eva.uow.ExecutionStep.ModelsUpdated
import com.razz.eva.uow.ExecutionStep.TransactionFinished
import com.razz.eva.uow.ExecutionStep.TransactionStarted
import com.razz.eva.uow.ExecutionStep.UowEventAdded
import com.razz.eva.uow.Noop
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.SpyRepo
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.events.UowEvent
import com.razz.eva.events.UowEvent.UowName
import com.razz.eva.test.domain.persistentStateV1
import com.razz.eva.uow.ExecutionStep.UowEventPublished
import com.razz.eva.uow.UowParams
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.Instant.now
import java.util.UUID.randomUUID

@Serializable
data class Params(val name: String) : UowParams<Params> {
    override fun serialization() = serializer()
}

class PersistingSpec : BehaviorSpec({

    val departmentId1 = randomDepartmentId()
    val bossId1 = EmployeeId(randomUUID())
    val departmentCreatedEvent1 = OwnedDepartmentCreated(
        departmentId = departmentId1,
        name = "KazahDepartment 1",
        headcount = 1,
        ration = BUBALEH,
        boss = bossId1,
    )
    val department1 = OwnedDepartment(
        id = departmentId1,
        name = "KazahDepartment 1",
        headcount = 1,
        ration = BUBALEH,
        boss = bossId1,
        modelState = newState(departmentCreatedEvent1),
    )
    val oldDepId = randomDepartmentId()
    val boss1 = Employee(
        bossId1, Name("Nursultan", "N"), oldDepId, "nursultan@001.kz", BUBALEH,
        persistentStateV1(),
    ).changeDepartment(department1)

    val departmentId2 = randomDepartmentId()
    val bossId2 = EmployeeId(randomUUID())
    val departmentCreatedEvent2 = OwnedDepartmentCreated(
        departmentId = departmentId2,
        name = "KazahDepartment 2",
        headcount = 1,
        ration = BUBALEH,
        boss = bossId2,
    )
    val department2 = OwnedDepartment(
        id = departmentId2,
        name = "KazahDepartment 2",
        headcount = 1,
        ration = BUBALEH,
        boss = bossId2,
        modelState = newState(departmentCreatedEvent2),
    )
    val boss2 = Employee(
        bossId2, Name("Vladimir", "P"), oldDepId, "vladimir@001.ru", BUBALEH,
        persistentStateV1(),
    ).changeDepartment(department2)

    val departmentId3 = randomDepartmentId()
    val bossId3 = EmployeeId(randomUUID())
    val department3 = OwnedDepartment(
        id = departmentId3,
        name = "KazahDepartment 3",
        headcount = 1,
        ration = BUBALEH,
        boss = bossId3,
        modelState = persistentState(V1, null),
    ).changeBoss(boss2)

    val params = Params("Nik")
    var supporstPipelining = false

    Given("Persisting lock'n'loaded") {
        val history = mutableListOf<ExecutionStep>()
        val topRepo = SpyRepo(history)

        @Suppress("UNCHECKED_CAST")
        val repos = ModelRepos(
            Department::class hasRepo topRepo as ModelRepository<*, Department<*>>,
            Employee::class hasRepo topRepo as ModelRepository<*, Employee>,
        )

        val txnManager = WithCtxConnectionTransactionManager(
            connection = { DummyConnection },
            beforeTxn = { history.add(TransactionStarted(it)) },
            afterTxn = { mode, _ -> history.add(TransactionFinished(mode)) },
            setPipelining = { supporstPipelining },
        )

        val eventsRepo = object : EventRepository {
            override suspend fun add(uowEvent: UowEvent) {
                history.add(UowEventAdded(uowEvent))
            }
        }

        val eventPublisher = object : EventPublisher {
            override suspend fun publish(uowEvent: UowEvent) {
                history.add(UowEventPublished(uowEvent))
            }
        }

        val persisting = Persisting(
            transactionManager = txnManager,
            modelRepos = repos,
            eventRepository = eventsRepo,
            eventPublisher = eventPublisher,
        )

        val now = now()
        val clock = mockk<Clock> {
            coEvery { instant() } returns now
        }

        listOf(true, false).forEach { pipelining ->
            supporstPipelining = pipelining
            history.clear()
            val persister: suspend (Boolean) -> Unit = { outOfOrder ->
                persisting.persist(
                    uowName = "Hoba",
                    params = params,
                    principal = TestPrincipal,
                    changes = ChangesAccumulator()
                        .withAdded(department1)
                        .withUpdated(boss1)
                        .withUnchanged(existingCreatedTestModel(param1 = "a", param2 = 1L))
                        .withUpdated(department3)
                        .withUnchanged(existingCreatedTestModel(param1 = "b", param2 = 2L))
                        .withAdded(department2)
                        .withUpdated(boss2)
                        .withResult(Unit)
                        .toPersist,
                    now = clock.instant(),
                    uowSupportsOutOfOrderPersisting = outOfOrder,
                )
            }

            When(
                "Principal persists changes of same model with${if (supporstPipelining) "" else " no"}" +
                    " pipelining support and with out of order persisting support",
            ) {
                persister(true)

                And(
                    "Persisting history matching grouped models and events from changes" +
                        " with context created from configured clock",
                ) {
                    history should {
                        it.size shouldBe 7
                        it[0] shouldBe TransactionStarted(REQUIRE_NEW)
                        it[1] shouldBe ModelsUpdated(transactionalContext(now), listOf(boss1, boss2))
                        it[2] shouldBe ModelsUpdated(transactionalContext(now), listOf(department3))
                        it[3] shouldBe ModelsAdded(transactionalContext(now), listOf(department1, department2))
                        it[4] should { eh ->
                            eh.shouldBeTypeOf<UowEventAdded>()
                            eh.uowEvent.occurredAt shouldBe now
                            eh.uowEvent.uowName shouldBe UowName("Hoba")
                            eh.uowEvent.modelEvents.map(Pair<*, *>::second) should { vals ->
                                vals.size shouldBe 5
                                vals[0] shouldBe OwnedDepartmentCreated(
                                    departmentId = departmentId1,
                                    name = "KazahDepartment 1",
                                    headcount = 1,
                                    ration = BUBALEH,
                                    boss = bossId1,
                                )
                                vals[1] shouldBe DepartmentChanged(bossId1, oldDepId, departmentId1)
                                vals[2] shouldBe BossChanged(departmentId3, bossId3, bossId2)
                                vals[3] shouldBe OwnedDepartmentCreated(
                                    departmentId = departmentId2,
                                    name = "KazahDepartment 2",
                                    headcount = 1,
                                    ration = BUBALEH,
                                    boss = bossId2,
                                )
                                vals[4] shouldBe DepartmentChanged(bossId2, oldDepId, departmentId2)
                            }
                        }
                        it[5] shouldBe TransactionFinished(REQUIRE_NEW)
                        it[6] should { eh ->
                            eh.shouldBeTypeOf<UowEventPublished>()
                            eh.uowEvent.occurredAt shouldBe now
                            eh.uowEvent.uowName shouldBe UowName("Hoba")
                            eh.uowEvent.modelEvents.map(Pair<*, *>::second) should { vals ->
                                vals.size shouldBe 5
                                vals[0] shouldBe OwnedDepartmentCreated(
                                    departmentId = departmentId1,
                                    name = "KazahDepartment 1",
                                    headcount = 1,
                                    ration = BUBALEH,
                                    boss = bossId1,
                                )
                                vals[1] shouldBe DepartmentChanged(bossId1, oldDepId, departmentId1)
                                vals[2] shouldBe BossChanged(departmentId3, bossId3, bossId2)
                                vals[3] shouldBe OwnedDepartmentCreated(
                                    departmentId = departmentId2,
                                    name = "KazahDepartment 2",
                                    headcount = 1,
                                    ration = BUBALEH,
                                    boss = bossId2,
                                )
                                vals[4] shouldBe DepartmentChanged(bossId2, oldDepId, departmentId2)
                            }
                        }
                    }
                }
            }

            history.clear()
            When(
                "Principal persists changes of same model with${if (supporstPipelining) "" else " no"}" +
                    " pipelining support and with no out of order persisting support",
            ) {
                persister(false)

                And(
                    "Persisting history matching grouped models and events from changes" +
                        " with context created from configured clock",
                ) {
                    history should {
                        it.size shouldBe 9
                        it[0] shouldBe TransactionStarted(REQUIRE_NEW)
                        it[1] shouldBe ModelAdded(transactionalContext(now), department1)
                        it[2] shouldBe ModelUpdated(transactionalContext(now), boss1)
                        it[3] shouldBe ModelUpdated(transactionalContext(now), department3)
                        it[4] shouldBe ModelAdded(transactionalContext(now), department2)
                        it[5] shouldBe ModelUpdated(transactionalContext(now), boss2)
                        it[6] should { eh ->
                            eh.shouldBeTypeOf<UowEventAdded>()
                            eh.uowEvent.occurredAt shouldBe now
                            eh.uowEvent.uowName shouldBe UowName("Hoba")
                            eh.uowEvent.modelEvents.map(Pair<*, *>::second) should { vals ->
                                vals.size shouldBe 5
                                vals[0] shouldBe OwnedDepartmentCreated(
                                    departmentId = departmentId1,
                                    name = "KazahDepartment 1",
                                    headcount = 1,
                                    ration = BUBALEH,
                                    boss = bossId1,
                                )
                                vals[1] shouldBe DepartmentChanged(bossId1, oldDepId, departmentId1)
                                vals[2] shouldBe BossChanged(departmentId3, bossId3, bossId2)
                                vals[3] shouldBe OwnedDepartmentCreated(
                                    departmentId = departmentId2,
                                    name = "KazahDepartment 2",
                                    headcount = 1,
                                    ration = BUBALEH,
                                    boss = bossId2,
                                )
                                vals[4] shouldBe DepartmentChanged(bossId2, oldDepId, departmentId2)
                            }
                        }
                        it[7] shouldBe TransactionFinished(REQUIRE_NEW)
                        it[8] should { eh ->
                            eh.shouldBeTypeOf<UowEventPublished>()
                            eh.uowEvent.occurredAt shouldBe now
                            eh.uowEvent.uowName shouldBe UowName("Hoba")
                            eh.uowEvent.modelEvents.map(Pair<*, *>::second) should { vals ->
                                vals.size shouldBe 5
                                vals[0] shouldBe OwnedDepartmentCreated(
                                    departmentId = departmentId1,
                                    name = "KazahDepartment 1",
                                    headcount = 1,
                                    ration = BUBALEH,
                                    boss = bossId1,
                                )
                                vals[1] shouldBe DepartmentChanged(bossId1, oldDepId, departmentId1)
                                vals[2] shouldBe BossChanged(departmentId3, bossId3, bossId2)
                                vals[3] shouldBe OwnedDepartmentCreated(
                                    departmentId = departmentId2,
                                    name = "KazahDepartment 2",
                                    headcount = 1,
                                    ration = BUBALEH,
                                    boss = bossId2,
                                )
                                vals[4] shouldBe DepartmentChanged(bossId2, oldDepId, departmentId2)
                            }
                        }
                    }
                }
            }

            listOf(true, false).forEach { outOfOrder ->

                history.clear()
                When(
                    "Principal persists noop changes with${if (supporstPipelining) "" else " no"} pipelining support" +
                        " and with${if (outOfOrder) "" else " no"} out of order persisting support",
                ) {
                    persisting.persist(
                        uowName = "Hoba",
                        params = params,
                        principal = TestPrincipal,
                        changes = listOf(
                            Noop(
                                OwnedDepartment(
                                    id = departmentId1,
                                    name = "KazahDepartment 1",
                                    headcount = 1,
                                    ration = BUBALEH,
                                    boss = bossId1,
                                    modelState = persistentStateV1(),
                                ),
                            ),
                            Noop(
                                OwnedDepartment(
                                    id = departmentId2,
                                    name = "KazahDepartment 2",
                                    headcount = 1,
                                    ration = BUBALEH,
                                    boss = bossId2,
                                    modelState = persistentStateV1(),
                                ),
                            ),
                        ),
                        now = clock.instant(),
                        uowSupportsOutOfOrderPersisting = outOfOrder,
                    )

                    And(
                        "Persisting history matching models and events from changes" +
                            " with context created from configured clock",
                    ) {
                        history should {
                            it.size shouldBe 4
                            it[0] shouldBe TransactionStarted(REQUIRE_NEW)
                            it[1] should { eh ->
                                eh.shouldBeTypeOf<UowEventAdded>()
                                eh.uowEvent.occurredAt shouldBe now
                                eh.uowEvent.uowName shouldBe UowName("Hoba")
                                eh.uowEvent.modelEvents shouldHaveSize 0
                            }
                            it[2] shouldBe TransactionFinished(REQUIRE_NEW)
                            it[3] should { eh ->
                                eh.shouldBeTypeOf<UowEventPublished>()
                                eh.uowEvent.occurredAt shouldBe now
                                eh.uowEvent.uowName shouldBe UowName("Hoba")
                                eh.uowEvent.modelEvents shouldHaveSize 0
                            }
                        }
                    }
                }
            }
        }
    }
})
