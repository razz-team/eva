package com.razz.eva.uow.func

import com.razz.eva.domain.mutating
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.Department
import com.razz.eva.domain.EntityKey
import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DeptAggregate
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.TestModel
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
import com.razz.eva.repository.TransactionalContext
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
import com.razz.eva.uow.ExecutionStep.EntitiesUpdated
import com.razz.eva.uow.ExecutionStep.EntityAdded
import com.razz.eva.uow.ExecutionStep.EntityDeleted
import com.razz.eva.uow.ExecutionStep.EntityDeletedByKey
import com.razz.eva.uow.ExecutionStep.EntityUpdated
import com.razz.eva.uow.ExecutionStep.ModelAdded
import com.razz.eva.uow.ExecutionStep.ModelUpdated
import com.razz.eva.uow.ExecutionStep.ModelsAdded
import com.razz.eva.uow.ExecutionStep.ModelsUpdated
import com.razz.eva.uow.ExecutionStep.TransactionFinished
import com.razz.eva.uow.ExecutionStep.TransactionStarted
import com.razz.eva.uow.ExecutionStep.UowEventAdded
import com.razz.eva.uow.ExecutionStep.UowEventPublished
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.PersistOrder
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.UpdateEntity
import com.razz.eva.uow.UpdateModel
import com.razz.eva.uow.SpyCreatableEntityRepo
import com.razz.eva.uow.SpyKeyDeletableEntityRepo
import com.razz.eva.uow.SpyModelRepo
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.params.kotlinx.KotlinxParamsSerializer
import com.razz.eva.uow.params.kotlinx.UowParams
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.Instant.now
import java.util.UUID.randomUUID

// Employees before every other model, by email descending; every other model ranks equal.
private data object EmployeesFirst : PersistOrder.Custom {
    override fun compare(a: Model<*, *>, b: Model<*, *>): Int = when {
        a is Employee && b is Employee -> b.email.compareTo(a.email)
        a is Employee -> -1
        b is Employee -> 1
        else -> 0
    }
}

// Records when each write starts and ends, and suspends in between, so writes that run in parallel interleave.
private class SuspendingRepo(
    private val steps: MutableList<String>,
) : ModelRepository<ModelId<Comparable<Any>>, Model<ModelId<Comparable<Any>>, *>> {

    private suspend fun <T> writing(what: String, result: T): T {
        steps.add("start $what")
        yield()
        steps.add("end $what")
        return result
    }

    override suspend fun find(id: ModelId<Comparable<Any>>): Model<ModelId<Comparable<Any>>, *>? = TODO("Not used")

    override suspend fun list(ids: Collection<ModelId<Comparable<Any>>>): List<Model<ModelId<Comparable<Any>>, *>> =
        TODO("Not used")

    override suspend fun <ME : Model<ModelId<Comparable<Any>>, *>> add(context: TransactionalContext, model: ME): ME =
        writing(model::class.simpleName.orEmpty(), model)

    override suspend fun <ME : Model<ModelId<Comparable<Any>>, *>> add(
        context: TransactionalContext,
        models: List<ME>,
    ): List<ME> = writing(models.first()::class.simpleName.orEmpty(), models)

    override suspend fun <ME : Model<ModelId<Comparable<Any>>, *>> update(
        context: TransactionalContext,
        model: ME,
    ): ME = writing(model::class.simpleName.orEmpty(), model)

    override suspend fun <ME : Model<ModelId<Comparable<Any>>, *>> update(
        context: TransactionalContext,
        models: List<ME>,
    ): List<ME> = writing(models.first()::class.simpleName.orEmpty(), models)
}

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
    ).mutating { changeDepartment(department1) }

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
    ).mutating { changeDepartment(department2) }

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
    val tagToUpdate = Tag.environmentTag(departmentId2.id, "staging")
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
            DeptAggregate::class hasRepo topRepo as ModelRepository<*, DeptAggregate<*>>,
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
            paramsSerializer = KotlinxParamsSerializer(),
        )

        val now = now()
        val clock = mockk<Clock> {
            coEvery { instant() } returns now
        }

        listOf(true, false).forEach { pipelining ->
            supporstPipelining = pipelining
            history.clear()
            val persister: suspend (PersistOrder) -> Unit = { persistOrder ->
                val (uowEvent1, _) = persisting.persist(
                    uowName = "Hoba",
                    params = params,
                    principal = TestPrincipal,
                    modelChanges = ChangesAccumulator().apply {
                        adding(department1)
                        updating(boss1)
                        claimingUnchanged(existingCreatedTestModel(param1 = "a", param2 = 1L))
                        updating(department3)
                        claimingUnchanged(existingCreatedTestModel(param1 = "b", param2 = 2L))
                        adding(department2)
                        updating(boss2)
                    }.withResult(Unit, null)
                        .modelChangesToPersist,
                    entityChanges = listOf(
                        AddEntity(tag1),
                        AddEntity(tag2),
                        UpdateEntity(tagToUpdate),
                        AddEntity(rationAllocation1),
                        AddEntity(rationAllocation2),
                        DeleteEntity(tagToDelete),
                    ),
                    now = clock.instant(),
                    persistOrder = persistOrder,
                    connectionMode = REQUIRE_NEW,
                )
                persisting.publish(uowEvent1)
            }

            When(
                "Principal persists changes of same model with${if (supporstPipelining) "" else " no"}" +
                    " pipelining support and with no promised write order",
            ) {
                persister(PersistOrder.Unspecified)

                And(
                    "Persisting history matching grouped models and events from changes" +
                        " with context created from configured clock",
                ) {
                    history should {
                        it.size shouldBe 11
                        it[0] shouldBe TransactionStarted(REQUIRE_NEW)
                        it[1] shouldBe ModelsUpdated(transactionalContext(now), listOf(boss1, boss2))
                        it[2] shouldBe ModelsUpdated(transactionalContext(now), listOf(department3))
                        it[3] shouldBe ModelsAdded(transactionalContext(now), listOf(department1, department2))
                        it[4] shouldBe EntitiesAdded(transactionalContext(now), listOf(tag1, tag2))
                        it[5] shouldBe EntitiesAdded(
                            transactionalContext(now),
                            listOf(rationAllocation1, rationAllocation2),
                        )
                        it[6] shouldBe EntitiesUpdated(transactionalContext(now), listOf(tagToUpdate))
                        it[7] shouldBe EntitiesDeleted(transactionalContext(now), listOf(tagToDelete))
                        it[8] should { eh ->
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
                        it[9] shouldBe TransactionFinished(REQUIRE_NEW)
                        it[10] should { eh ->
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
                    " pipelining support and in registration order",
            ) {
                persister(PersistOrder.Registration)

                And(
                    "Persisting history matching grouped models and events from changes" +
                        " with context created from configured clock",
                ) {
                    history should {
                        it.size shouldBe 15
                        it[0] shouldBe TransactionStarted(REQUIRE_NEW)
                        it[1] shouldBe ModelAdded(transactionalContext(now), department1)
                        it[2] shouldBe ModelUpdated(transactionalContext(now), boss1)
                        it[3] shouldBe ModelUpdated(transactionalContext(now), department3)
                        it[4] shouldBe ModelAdded(transactionalContext(now), department2)
                        it[5] shouldBe ModelUpdated(transactionalContext(now), boss2)
                        it[6] shouldBe EntityAdded(transactionalContext(now), tag1)
                        it[7] shouldBe EntityAdded(transactionalContext(now), tag2)
                        it[8] shouldBe EntityUpdated(transactionalContext(now), tagToUpdate)
                        it[9] shouldBe EntityAdded(transactionalContext(now), rationAllocation1)
                        it[10] shouldBe EntityAdded(transactionalContext(now), rationAllocation2)
                        it[11] shouldBe EntityDeleted(transactionalContext(now), tagToDelete)
                        it[12] should { eh ->
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
                        it[13] shouldBe TransactionFinished(REQUIRE_NEW)
                        it[14] should { eh ->
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
                    " pipelining support and in a custom order",
            ) {
                persister(EmployeesFirst)

                And("Models are written one by one in the order's sort, ties in registration order") {
                    history should {
                        it.size shouldBe 15
                        it[0] shouldBe TransactionStarted(REQUIRE_NEW)
                        it[1] shouldBe ModelUpdated(transactionalContext(now), boss2)
                        it[2] shouldBe ModelUpdated(transactionalContext(now), boss1)
                        it[3] shouldBe ModelAdded(transactionalContext(now), department1)
                        it[4] shouldBe ModelUpdated(transactionalContext(now), department3)
                        it[5] shouldBe ModelAdded(transactionalContext(now), department2)
                        it[6] shouldBe EntityAdded(transactionalContext(now), tag1)
                        it[7] shouldBe EntityAdded(transactionalContext(now), tag2)
                        it[8] shouldBe EntityUpdated(transactionalContext(now), tagToUpdate)
                        it[9] shouldBe EntityAdded(transactionalContext(now), rationAllocation1)
                        it[10] shouldBe EntityAdded(transactionalContext(now), rationAllocation2)
                        it[11] shouldBe EntityDeleted(transactionalContext(now), tagToDelete)
                        it[13] shouldBe TransactionFinished(REQUIRE_NEW)
                    }
                }

                And("Events are stored and published in registration order") {
                    val stored = history[12].shouldBeTypeOf<UowEventAdded>().uowEvent
                    val published = history[14].shouldBeTypeOf<UowEventPublished>().uowEvent
                    listOf(stored, published).forEach { uowEvent ->
                        uowEvent.modelEvents.map(Pair<*, *>::second) shouldBe listOf(
                            departmentCreatedEvent1,
                            DepartmentChanged(bossId1, oldDepId, departmentId1),
                            BossChanged(departmentId3, bossId3, bossId2),
                            departmentCreatedEvent2,
                            DepartmentChanged(bossId2, oldDepId, departmentId2),
                        )
                    }
                }
            }

            history.clear()
            When(
                "Principal persists an aggregate with owned models with${if (supporstPipelining) "" else " no"}" +
                    " pipelining support and in a custom order",
            ) {
                val staff1 = Employee(
                    EmployeeId(randomUUID()), Name("Ada", "L"), oldDepId, "amy@001.kz", BUBALEH,
                    persistentStateV1(),
                ).mutating { changeDepartment(department1) }
                val staff2 = Employee(
                    EmployeeId(randomUUID()), Name("Grace", "H"), oldDepId, "zed@001.kz", BUBALEH,
                    persistentStateV1(),
                ).mutating { changeDepartment(department1) }
                val aggregate = DeptAggregate(
                    departmentId1, "Stored", bossId1, 2, BUBALEH, listOf(staff1, staff2), persistentStateV1(),
                ).rename("Renamed")
                persisting.persist(
                    uowName = "Aggregate",
                    params = params,
                    principal = TestPrincipal,
                    modelChanges = ChangesAccumulator().apply { updating(aggregate) }
                        .withResult(Unit, null)
                        .modelChangesToPersist,
                    entityChanges = listOf(),
                    now = clock.instant(),
                    persistOrder = EmployeesFirst,
                    connectionMode = REQUIRE_NEW,
                )

                Then("The order sorts the owned models with the root, and events keep registration order") {
                    history should {
                        it.size shouldBe 6
                        it[1] shouldBe ModelUpdated(transactionalContext(now), staff2)
                        it[2] shouldBe ModelUpdated(transactionalContext(now), staff1)
                        it[3] shouldBe ModelUpdated(transactionalContext(now), aggregate)
                        it[4].shouldBeTypeOf<UowEventAdded>().uowEvent.modelEvents.map(Pair<*, *>::second) shouldBe
                            aggregate.modelEvents() + staff1.modelEvents() + staff2.modelEvents()
                    }
                }
            }

            history.clear()
            When(
                "Principal persists changes with${if (supporstPipelining) "" else " no"} pipelining support" +
                    " in a custom order that is not a total order",
            ) {
                val models = (0L until 32L).map { existingCreatedTestModel(param1 = "m", param2 = it) }
                val inconsistent = PersistOrder.Custom { a, b ->
                    Integer.compare(
                        ((a as TestModel.CreatedTestModel).param2 * 7 % 5).toInt(),
                        ((b as TestModel.CreatedTestModel).param2 * 11 % 5).toInt(),
                    )
                }
                val ex = shouldThrow<IllegalArgumentException> {
                    persisting.persist(
                        uowName = "Inconsistent",
                        params = params,
                        principal = TestPrincipal,
                        modelChanges = models.map { UpdateModel(it.changeParam1("n")) },
                        entityChanges = listOf(),
                        now = clock.instant(),
                        persistOrder = inconsistent,
                        connectionMode = REQUIRE_NEW,
                    )
                }

                Then("The sort fails before the transaction starts, and nothing is written") {
                    ex.message shouldBe "Comparison method violates its general contract!"
                    history shouldBe listOf()
                }
            }

            listOf(PersistOrder.Unspecified, PersistOrder.Registration).forEach { persistOrder ->

                history.clear()
                When(
                    "Principal persists noop changes with${if (supporstPipelining) "" else " no"} pipelining support" +
                        " and persist order $persistOrder",
                ) {
                    val (uowEvent2, _) = persisting.persist(
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
                        persistOrder = persistOrder,
                        connectionMode = REQUIRE_NEW,
                    )
                    persisting.publish(uowEvent2)

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
        When("Principal persists single key deletion with no promised write order") {
            val (uowEvent3, _) = persisting.persist(
                uowName = "DeleteByKey",
                params = params,
                principal = TestPrincipal,
                modelChanges = listOf(),
                entityChanges = listOf(
                    DeleteEntityByKey(key1, Tag::class),
                ),
                now = clock.instant(),
                persistOrder = PersistOrder.Unspecified,
                connectionMode = REQUIRE_NEW,
            )
            persisting.publish(uowEvent3)

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
        When("Principal persists multiple key deletions with no promised write order") {
            val (uowEvent4, _) = persisting.persist(
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
                persistOrder = PersistOrder.Unspecified,
                connectionMode = REQUIRE_NEW,
            )
            persisting.publish(uowEvent4)

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
            val (uowEvent5, _) = persisting.persist(
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
                persistOrder = PersistOrder.Unspecified,
                connectionMode = REQUIRE_NEW,
            )
            persisting.publish(uowEvent5)

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
        When("Principal persists key deletion in registration order") {
            val (uowEvent6, _) = persisting.persist(
                uowName = "SequentialDeleteByKey",
                params = params,
                principal = TestPrincipal,
                modelChanges = listOf(),
                entityChanges = listOf(
                    DeleteEntityByKey(key1, Tag::class),
                    DeleteEntityByKey(key2, Tag::class),
                ),
                now = clock.instant(),
                persistOrder = PersistOrder.Registration,
                connectionMode = REQUIRE_NEW,
            )
            persisting.publish(uowEvent6)

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

    Given("A transaction manager that pipelines and a repository whose writes suspend") {
        val steps = mutableListOf<String>()
        val repo = SuspendingRepo(steps)
        @Suppress("UNCHECKED_CAST")
        val persisting = Persisting(
            transactionManager = WithCtxConnectionTransactionManager(
                connection = { DummyConnection },
                setPipelining = { true },
            ),
            modelRepos = ModelRepos(
                Department::class hasRepo repo as ModelRepository<*, Department<*>>,
                Employee::class hasRepo repo as ModelRepository<*, Employee>,
            ),
            entityRepos = EntityRepos(),
            eventRepository = object : EventRepository {
                override suspend fun add(uowEvent: UowEvent) = Unit
            },
            eventPublisher = object : EventPublisher {
                override suspend fun publish(uowEvent: UowEvent) = Unit
            },
            paramsSerializer = KotlinxParamsSerializer(),
        )
        suspend fun persist(persistOrder: PersistOrder) {
            steps.clear()
            persisting.persist(
                uowName = "Suspending",
                params = params,
                principal = TestPrincipal,
                modelChanges = listOf(UpdateModel(department3), UpdateModel(boss1)),
                entityChanges = listOf(),
                now = now(),
                persistOrder = persistOrder,
                connectionMode = REQUIRE_NEW,
            )
        }

        When("Principal persists in registration order") {
            persist(PersistOrder.Registration)

            Then("Each write ends before the next starts") {
                steps shouldBe listOf("start OwnedDepartment", "end OwnedDepartment", "start Employee", "end Employee")
            }
        }

        When("Principal persists in a custom order") {
            persist(EmployeesFirst)

            Then("Each write ends before the next starts, in the order's sort") {
                steps shouldBe listOf("start Employee", "end Employee", "start OwnedDepartment", "end OwnedDepartment")
            }
        }

        When("Principal persists with no promised write order") {
            persist(PersistOrder.Unspecified)

            Then("The batches run in parallel") {
                steps shouldBe listOf("start OwnedDepartment", "start Employee", "end OwnedDepartment", "end Employee")
            }
        }
    }
})
