package com.razz.eva.repository

import com.razz.eva.domain.Department
import com.razz.eva.domain.Department.OrphanedDepartment
import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent
import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.NewState.Companion.newState
import com.razz.eva.domain.ModelState.PersistentState
import com.razz.eva.domain.Ration
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.domain.ModelId
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.StoreExecuted
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.repository.TransactionalContext.Companion.transactionalContext
import com.razz.eva.test.schema.Tables.DEPARTMENTS
import com.razz.eva.test.schema.enums.DepartmentsState
import com.razz.eva.test.schema.tables.records.DepartmentsRecord
import com.razz.jooq.converter.InstantConverter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jooq.DMLQuery
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SQLDialect.POSTGRES
import org.jooq.Select
import org.jooq.StoreQuery
import org.jooq.Table
import org.jooq.conf.ParamType.INLINED
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import java.sql.SQLException
import java.time.Instant.MAX
import java.time.Instant.MIN
import java.time.Instant.now
import java.util.*
import java.util.UUID.randomUUID

class JooqBaseRepositoryNegativeSpec : BehaviorSpec({
    Given("JooqBaseRepository with hacked queryExecutor") {
        val dslContext = DSL.using(POSTGRES)
        val queryExecutor = FakeMemorizingQueryExecutor()

        lateinit var addedDep: OwnedDepartment
        val addContext = transactionalContext(now())
        val updateContext = transactionalContext(now())

        val repo = BadDepartmentRepository(queryExecutor, dslContext)

        And("Model of NewState") {
            val depId = randomDepartmentId()
            val bossId = EmployeeId(randomUUID())
            val dep = OwnedDepartment(
                id = depId,
                name = "store me in the repo",
                boss = bossId,
                headcount = 1,
                ration = BUBALEH,
                modelState = newState(
                    OwnedDepartmentCreated(
                        departmentId = depId,
                        name = "store me in the repo",
                        boss = bossId,
                        headcount = 1,
                        ration = BUBALEH,
                    ),
                ),
            )

            And("Query executor accepts good insert") {
                queryExecutor.expectQueryFor(
                    DepartmentsRecord().apply {
                        setId(depId.id)
                        setName(dep.name)
                        setBoss(dep.boss!!.id)
                        setHeadcount(dep.headcount)
                        setRation(dep.ration.name)
                        setState(DepartmentsState.OWNED)
                        setRecordUpdatedAt(addContext.startedAt)
                        setRecordCreatedAt(addContext.startedAt)
                        setVersion(V1.version)
                    },
                )

                When("Principal saving model through malicious repository") {
                    addedDep = repo.add(addContext, dep)
                    val recordCreatedAt = InstantConverter.instance.to(addContext.startedAt)
                    val boss = requireNotNull(dep.boss)

                    Then(
                        "Query executor should receive record with RECORD_CREATED_AT and RECORD_UPDATED_AT" +
                            " matching context.startedAt and V1",
                    ) {
                        val insert = queryExecutor.lastExecution.shouldBeTypeOf<StoreExecuted>()
                        insert.jooqQuery.getSQL(INLINED) shouldBe """
                            insert into "departments"
                            ("id",
                             "name",
                             "boss",
                             "headcount",
                             "ration",
                             "state",
                             "record_updated_at",
                             "record_created_at",
                             "version")
                            values 
                            (cast('${dep.id().id}' as uuid),
                             'store me in the repo',
                             cast('${boss.id}' as uuid),
                             1,
                             'BUBALEH',
                             cast('OWNED' as "departments_state"),
                             timestamp '$recordCreatedAt',
                             timestamp '$recordCreatedAt',
                             1)
                     """.trim().replace(Regex("\\s+"), " ")
                    }
                }
            }

            And("Query executor accepts good update") {
                queryExecutor.expectQueryFor(
                    DepartmentsRecord().apply {
                        setId(depId.id)
                        setName("UPDATE TEST")
                        setBoss(dep.boss!!.id)
                        setHeadcount(dep.headcount)
                        setRation(dep.ration.name)
                        setState(DepartmentsState.OWNED)
                        setRecordUpdatedAt(updateContext.startedAt)
                        setRecordCreatedAt(addContext.startedAt)
                        setVersion(2)
                    },
                )

                When("Principal updating model through malicious repository") {
                    repo.update(updateContext, addedDep.rename("UPDATE TEST"))
                    val recordUpdatedAt = InstantConverter.instance.to(updateContext.startedAt)

                    Then(
                        "Query executor should receive record with RECORD_CREATED_AT and RECORD_UPDATED_AT" +
                            " matching context.startedAt and V2",
                    ) {
                        val update = queryExecutor.lastExecution.shouldBeTypeOf<StoreExecuted>()
                        update.jooqQuery.getSQL(INLINED) shouldBe """
                            update "departments" set 
                            "name" = 'UPDATE TEST',
                            "record_updated_at" = timestamp '$recordUpdatedAt',
                            "version" = 2
                            where ("departments"."id" = cast('${dep.id().id}' as uuid) and "departments"."version" = 1)
                        """.trim().replace(Regex("\\s+"), " ")
                    }
                }
            }
        }
    }

    Given("JooqBaseRepository with queryExecutor failing selects with a connection error") {
        val dslContext = DSL.using(POSTGRES)
        val connectionLoss = DataAccessException("select failed", SQLException("connection reset", "08006"))
        val queryExecutor = object : QueryExecutor {
            override suspend fun <R : Record> executeSelect(
                dslContext: DSLContext,
                jooqQuery: Select<R>,
                table: Table<R>,
            ): List<R> = throw connectionLoss

            override suspend fun <RIN : Record, ROUT : Record> executeStore(
                dslContext: DSLContext,
                jooqQuery: StoreQuery<RIN>,
                table: Table<ROUT>,
            ): List<ROUT> = TODO("NEVER HAPPENS")

            override suspend fun <R : Record> executeQuery(
                dslContext: DSLContext,
                jooqQuery: DMLQuery<R>,
            ): Int = TODO("NEVER HAPPENS")

            override fun extractConstraintName(ex: Exception): QueryExecutor.Constraint? = null

            override fun extractUniqueConstraintName(ex: Exception, table: Table<*>): QueryExecutor.Constraint? =
                null

            override fun extractModelException(
                ex: Exception,
                table: Table<*>,
                modelId: ModelId<*>,
            ): PersistenceException? = null

            override fun extractConnectionException(ex: Exception): PersistenceException.ConnectionException? =
                (ex as? DataAccessException)?.let { PersistenceException.ConnectionException(it) }
        }
        val repo = BadDepartmentRepository(queryExecutor, dslContext)

        When("Principal finds department by id") {
            Then("ConnectionException with the original cause is thrown") {
                val ex = shouldThrow<PersistenceException.ConnectionException> {
                    repo.find(randomDepartmentId())
                }
                ex.cause shouldBe connectionLoss
            }
        }
    }
})

class BadDepartmentRepository(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext,
) : JooqStatefulModelRepository<
    UUID, DepartmentId, Department<*>, DepartmentEvent, DepartmentsRecord, DepartmentsState,
    >(
    queryExecutor = queryExecutor,
    dslContext = dslContext,
    table = DEPARTMENTS,
    stripNotModifiedFields = true,
) {
    override fun stateOf(model: Department<*>): DepartmentsState {
        return when (model) {
            is OwnedDepartment -> DepartmentsState.OWNED
            is OrphanedDepartment -> DepartmentsState.ORPHANED
        }
    }

    override fun toRecord(model: Department<*>): DepartmentsRecord =
        DepartmentsRecord().apply {
            name = model.name
            boss = model.boss?.id
            headcount = model.headcount
            ration = model.ration.name
            setRecordCreatedAt(MIN)
            setRecordUpdatedAt(MAX)
            setVersion(-1L)
        }

    override fun fromRecord(
        record: DepartmentsRecord,
        modelState: PersistentState<DepartmentId, DepartmentEvent>,
    ): Department<*> {
        when (record.boss) {
            null -> return OrphanedDepartment(
                DepartmentId(record.id),
                record.name,
                record.headcount,
                Ration.valueOf(record.ration),
                modelState,
            )
            else -> return OwnedDepartment(
                DepartmentId(record.id),
                record.name,
                EmployeeId(record.boss),
                record.headcount,
                Ration.valueOf(record.ration),
                modelState,
            )
        }
    }
}
