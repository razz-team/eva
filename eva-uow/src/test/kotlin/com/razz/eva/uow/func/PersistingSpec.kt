package com.razz.eva.uow.func

import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.Department
import com.razz.eva.domain.EntityKey
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
import com.razz.eva.domain.RationAllocation
import com.razz.eva.domain.Tag
import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.events.EventPublisher
import com.razz.eva.events.UowEvent
import com.razz.eva.events.UowEvent.UowName
import com.razz.eva.persistence.ConnectionMode.REQUIRE_NEW
import com.razz.eva.persistence.DummyConnection
import com.razz.eva.persistence.WithCtxConnectionTransactionManager
import com.razz.eva.repository.DeletableEntityRepository
import com.razz.eva.repository.EntityRepos
import com.razz.eva.repository.EntityRepository
import com.razz.eva.repository.EventRepository
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.ModelRepository
import com.razz.eva.repository.TransactionalContext.Companion.transactionalContext
import com.razz.eva.repository.hasEntityRepo
import com.razz.eva.repository.hasRepo
import com.razz.eva.test.domain.persistentStateV1
import com.razz.eva.uow.AddEntity
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.uow.DeleteEntity
import com.razz.eva.uow.DeleteEntityByKey
import com.razz.eva.uow.ExecutionStep
import com.razz.eva.uow.ExecutionStep.EntitiesAdded
import com.razz.eva.uow.ExecutionStep.EntitiesDeleted
import com.razz.eva.uow.ExecutionStep.EntitiesDeletedByKey
import com.razz.eva.uow.ExecutionStep.EntityAdded
import com.razz.eva.uow.ExecutionStep.EntityDeleted
import com.razz.eva.uow.ExecutionStep.EntityDeletedByKey
import com.razz.eva.uow.ExecutionStep.ModelAdded
import com.razz.eva.uow.ExecutionStep.ModelUpdated
import com.razz.eva.uow.ExecutionStep.ModelsAdded
import com.razz.eva.uow.ExecutionStep.ModelsUpdated
import com.razz.eva.uow.ExecutionStep.TransactionFinished
import com.razz.eva.uow.ExecutionStep.TransactionStarted
import com.razz.eva.uow.ExecutionStep.UowEventAdded
import com.razz.eva.uow.ExecutionStep.UowEventPublished
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.SpyCreatableEntityRepo
import com.razz.eva.uow.SpyKeyDeletableEntityRepo
import com.razz.eva.uow.SpyModelRepo
import com.razz.eva.uow.TestPrincipal
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

class PersistingSpec : BehaviorSpec({

    @Serializable
    data class Params(val name: String) : UowParams<Params> {
        override fun serialization() = serializer()
    }

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

    val tag1 = Tag.environmentTag(departmentId1.id, "production")
    val tag2 = Tag.priorityTag(departmentId2.id, 1)
    val tagToDelete = Tag.tag(departmentId3.id, "deprecated", "true")

    val rationAllocation1 = RationAllocation.allocation(bossId1, BUBALEH, java.time.LocalDate.now(), 3)
    val rationAllocation2 = RationAllocation.allocation(bossId2, BUBALEH, java.time.LocalDate.now(), 5)

    val params = Params("Nik")
    var supporstPipelining = false

    Given("Persisting lock'n'loaded") {
        val history = mutableListOf<ExecutionStep>()
        val topRepo = SpyModelRepo(history)
        val deletableEntityRepo = SpyKeyDeletableEntityRepo<DeletableEntity, EntityKey<DeletableEntity>>(history)
        val creatableEntityRepo = SpyCreatableEntityRepo(history)

        @Suppress("UNCHECKED_CAST")
        val modelRepos = ModelRepos(
            Department::class hasRepo topRepo as ModelRepository<*, Department<*>>,
            Employee::class hasRepo topRepo as ModelRepository<*, Employee>,
        )
        @Suppress("UNCHECKED_CAST")
        val entityRepos = EntityRepos(
            Tag::class hasEntityRepo deletableEntityRepo as DeletableEntityRepository<Tag>,
            RationAllocation::class hasEntityRepo creatableEntityRepo as EntityRepository<RationAllocation>,
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
            modelRepos = modelRepos,
            entityRepos = entityRepos,
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
                    modelChanges = ChangesAccumulator()
                        .withAddedModel(department1)
                        .withUpdatedModel(boss1)
                        .withUnchangedModel(existingCreatedTestModel(param1 = "a", param2 = 1L))
                        .withUpdatedModel(department3)
                        .withUnchangedModel(existingCreatedTestModel(param1 = "b", param2 = 2L))
                        .withAddedModel(department2)
                        .withUpdatedModel(boss2)
                        .withResult(Unit)
                        .modelChangesToPersist,
                    entityChanges = listOf(
                        AddEntity(tag1),
                        AddEntity(tag2),
                        AddEntity(rationAllocation1),
                        AddEntity(rationAllocation2),
                        DeleteEntity(tagToDelete),
                    ),
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
                        it.size shouldBe 10
                        it[0] shouldBe TransactionStarted(REQUIRE_NEW)
                        it[1] shouldBe ModelsUpdated(transactionalContext(now), listOf(boss1, boss2))
                        it[2] shouldBe ModelsUpdated(transactionalContext(now), listOf(department3))
                        it[3] shouldBe ModelsAdded(transactionalContext(now), listOf(department1, department2))
                        it[4] shouldBe EntitiesAdded(transactionalContext(now), listOf(tag1, tag2))
                        it[5] shouldBe EntitiesAdded(
                            transactionalContext(now),
                            listOf(rationAllocation1, rationAllocation2),
                        )
                        it[6] shouldBe EntitiesDeleted(transactionalContext(now), listOf(tagToDelete))
                        it[7] should { eh ->
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
                        it[8] shouldBe TransactionFinished(REQUIRE_NEW)
                        it[9] should { eh ->
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
                        it.size shouldBe 14
                        it[0] shouldBe TransactionStarted(REQUIRE_NEW)
                        it[1] shouldBe ModelAdded(transactionalContext(now), department1)
                        it[2] shouldBe ModelUpdated(transactionalContext(now), boss1)
                        it[3] shouldBe ModelUpdated(transactionalContext(now), department3)
                        it[4] shouldBe ModelAdded(transactionalContext(now), department2)
                        it[5] shouldBe ModelUpdated(transactionalContext(now), boss2)
                        it[6] shouldBe EntityAdded(transactionalContext(now), tag1)
                        it[7] shouldBe EntityAdded(transactionalContext(now), tag2)
                        it[8] shouldBe EntityAdded(transactionalContext(now), rationAllocation1)
                        it[9] shouldBe EntityAdded(transactionalContext(now), rationAllocation2)
                        it[10] shouldBe EntityDeleted(transactionalContext(now), tagToDelete)
                        it[11] should { eh ->
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
                        it[12] shouldBe TransactionFinished(REQUIRE_NEW)
                        it[13] should { eh ->
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
                        modelChanges = listOf(
                            NoopModel(
                                OwnedDepartment(
                                    id = departmentId1,
                                    name = "KazahDepartment 1",
                                    headcount = 1,
                                    ration = BUBALEH,
                                    boss = bossId1,
                                    modelState = persistentStateV1(),
                                ),
                            ),
                            NoopModel(
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
                        entityChanges = listOf(),
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

        val keySubjectId = randomUUID()
        val key1 = Tag.Key(keySubjectId, "env")
        val key2 = Tag.Key(keySubjectId, "priority")
        val key3 = Tag.Key(keySubjectId, "deprecated")

        history.clear()
        When("Principal persists single key deletion with out of order support") {
            persisting.persist(
                uowName = "DeleteByKey",
                params = params,
                principal = TestPrincipal,
                modelChanges = listOf(),
                entityChanges = listOf(
                    DeleteEntityByKey(key1, Tag::class),
                ),
                now = clock.instant(),
                uowSupportsOutOfOrderPersisting = true,
            )

            Then("Persisting history contains key deletion step") {
                history should {
                    it.size shouldBe 5
                    it[0] shouldBe TransactionStarted(REQUIRE_NEW)
                    it[1] shouldBe EntitiesDeletedByKey(transactionalContext(now), listOf(key1))
                    it[2] should { eh ->
                        eh.shouldBeTypeOf<UowEventAdded>()
                        eh.uowEvent.uowName shouldBe UowName("DeleteByKey")
                    }
                    it[3] shouldBe TransactionFinished(REQUIRE_NEW)
                    it[4] should { eh ->
                        eh.shouldBeTypeOf<UowEventPublished>()
                        eh.uowEvent.uowName shouldBe UowName("DeleteByKey")
                    }
                }
            }
        }

        history.clear()
        When("Principal persists multiple key deletions with out of order support") {
            persisting.persist(
                uowName = "BatchDeleteByKey",
                params = params,
                principal = TestPrincipal,
                modelChanges = listOf(),
                entityChanges = listOf(
                    DeleteEntityByKey(key1, Tag::class),
                    DeleteEntityByKey(key2, Tag::class),
                    DeleteEntityByKey(key3, Tag::class),
                ),
                now = clock.instant(),
                uowSupportsOutOfOrderPersisting = true,
            )

            Then("Persisting history contains batched key deletion step") {
                history should {
                    it.size shouldBe 5
                    it[0] shouldBe TransactionStarted(REQUIRE_NEW)
                    it[1] shouldBe EntitiesDeletedByKey(transactionalContext(now), listOf(key1, key2, key3))
                    it[2] should { eh ->
                        eh.shouldBeTypeOf<UowEventAdded>()
                        eh.uowEvent.uowName shouldBe UowName("BatchDeleteByKey")
                    }
                    it[3] shouldBe TransactionFinished(REQUIRE_NEW)
                    it[4] should { eh ->
                        eh.shouldBeTypeOf<UowEventPublished>()
                        eh.uowEvent.uowName shouldBe UowName("BatchDeleteByKey")
                    }
                }
            }
        }

        history.clear()
        When("Principal persists mixed entity add and key deletion") {
            val newTag = Tag.tag(keySubjectId, "new-tag", "value")
            persisting.persist(
                uowName = "MixedOperations",
                params = params,
                principal = TestPrincipal,
                modelChanges = listOf(),
                entityChanges = listOf(
                    AddEntity(newTag),
                    DeleteEntityByKey(key1, Tag::class),
                    DeleteEntityByKey(key2, Tag::class),
                ),
                now = clock.instant(),
                uowSupportsOutOfOrderPersisting = true,
            )

            Then("Persisting history contains both add and key deletion steps") {
                history should {
                    it.size shouldBe 6
                    it[0] shouldBe TransactionStarted(REQUIRE_NEW)
                    it[1] shouldBe EntitiesAdded(transactionalContext(now), listOf(newTag))
                    it[2] shouldBe EntitiesDeletedByKey(transactionalContext(now), listOf(key1, key2))
                    it[3] should { eh ->
                        eh.shouldBeTypeOf<UowEventAdded>()
                        eh.uowEvent.uowName shouldBe UowName("MixedOperations")
                    }
                    it[4] shouldBe TransactionFinished(REQUIRE_NEW)
                    it[5] should { eh ->
                        eh.shouldBeTypeOf<UowEventPublished>()
                        eh.uowEvent.uowName shouldBe UowName("MixedOperations")
                    }
                }
            }
        }

        history.clear()
        When("Principal persists key deletion without out of order support") {
            persisting.persist(
                uowName = "SequentialDeleteByKey",
                params = params,
                principal = TestPrincipal,
                modelChanges = listOf(),
                entityChanges = listOf(
                    DeleteEntityByKey(key1, Tag::class),
                    DeleteEntityByKey(key2, Tag::class),
                ),
                now = clock.instant(),
                uowSupportsOutOfOrderPersisting = false,
            )

            Then("Persisting history contains individual key deletion steps") {
                history should {
                    it.size shouldBe 6
                    it[0] shouldBe TransactionStarted(REQUIRE_NEW)
                    it[1] shouldBe EntityDeletedByKey(transactionalContext(now), key1)
                    it[2] shouldBe EntityDeletedByKey(transactionalContext(now), key2)
                    it[3] should { eh ->
                        eh.shouldBeTypeOf<UowEventAdded>()
                        eh.uowEvent.uowName shouldBe UowName("SequentialDeleteByKey")
                    }
                    it[4] shouldBe TransactionFinished(REQUIRE_NEW)
                    it[5] should { eh ->
                        eh.shouldBeTypeOf<UowEventPublished>()
                        eh.uowEvent.uowName shouldBe UowName("SequentialDeleteByKey")
                    }
                }
            }
        }
    }
})
