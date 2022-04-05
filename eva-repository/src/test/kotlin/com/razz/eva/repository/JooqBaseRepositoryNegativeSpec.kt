package com.razz.eva.repository

import com.razz.eva.domain.Department
import com.razz.eva.domain.Department.OrphanedDepartment
import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent
import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.EntityState.NewState.Companion.newState
import com.razz.eva.domain.EntityState.PersistentState
import com.razz.eva.domain.Ration
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.StoreExecuted
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.repository.TransactionalContext.Companion.transactionalContext
import com.razz.eva.test.db.Tables.DEPARTMENTS
import com.razz.eva.test.db.enums.DepartmentsState
import com.razz.eva.test.db.tables.records.DepartmentsRecord
import com.razz.jooq.converter.InstantConverter
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jooq.DSLContext
import org.jooq.SQLDialect.POSTGRES
import org.jooq.conf.ParamType.INLINED
import org.jooq.impl.DSL
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
                entityState = newState(
                    OwnedDepartmentCreated(
                        departmentId = depId,
                        name = "store me in the repo",
                        boss = bossId,
                        headcount = 1,
                        ration = BUBALEH
                    )
                )
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
                    }
                )

                When("Principal saving model through malicious repository") {
                    addedDep = repo.add(addContext, dep)
                    val recordCreatedAt = InstantConverter.instance.to(addContext.startedAt)
                    val boss = requireNotNull(dep.boss)

                    Then(
                        "Query executor should receive record with RECORD_CREATED_AT and RECORD_UPDATED_AT" +
                            " matching context.startedAt and V1"
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
                            ('${dep.id().id}',
                             'store me in the repo',
                             '${boss.id}',
                             1,
                             'BUBALEH',
                             'OWNED',
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
                    }
                )

                When("Principal updating model through malicious repository") {
                    repo.update(updateContext, addedDep.rename("UPDATE TEST"))
                    val recordUpdatedAt = InstantConverter.instance.to(updateContext.startedAt)
                    val boss = requireNotNull(dep.boss)

                    Then(
                        "Query executor should receive record with RECORD_CREATED_AT and RECORD_UPDATED_AT" +
                            " matching context.startedAt and V2"
                    ) {
                        val update = queryExecutor.lastExecution.shouldBeTypeOf<StoreExecuted>()
                        update.jooqQuery.getSQL(INLINED) shouldBe """
                            update "departments" set 
                            "name" = 'UPDATE TEST',
                            "boss" = '${boss.id}',
                            "headcount" = 1,
                            "ration" = 'BUBALEH',
                            "state" = 'OWNED',
                            "record_updated_at" = timestamp '$recordUpdatedAt',
                            "version" = 2
                            where ("departments"."id" = '${dep.id().id}' and "departments"."version" = 1)
                        """.trim().replace(Regex("\\s+"), " ")
                    }
                }
            }
        }
    }
})

class BadDepartmentRepository(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext
) : JooqStatefulModelRepository<
    UUID, DepartmentId, Department<*>, DepartmentEvent, DepartmentsRecord, DepartmentsState
    >(
    queryExecutor,
    dslContext,
    DEPARTMENTS
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
        entityState: PersistentState<DepartmentId, DepartmentEvent>
    ): Department<*> {
        when (record.boss) {
            null -> return OrphanedDepartment(
                DepartmentId(record.id),
                record.name,
                record.headcount,
                Ration.valueOf(record.ration),
                entityState
            )
            else -> return OwnedDepartment(
                DepartmentId(record.id),
                record.name,
                EmployeeId(record.boss),
                record.headcount,
                Ration.valueOf(record.ration),
                entityState
            )
        }
    }
}
