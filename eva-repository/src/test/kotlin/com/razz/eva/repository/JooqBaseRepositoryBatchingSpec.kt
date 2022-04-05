package com.razz.eva.repository

import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.EntityState.NewState.Companion.newState
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Ration.SHAKSHOUKA
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.StoreExecuted
import com.razz.eva.repository.TransactionalContext.Companion.transactionalContext
import com.razz.eva.test.db.enums.DepartmentsState
import com.razz.eva.test.db.tables.records.DepartmentsRecord
import com.razz.jooq.converter.InstantConverter
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jooq.SQLDialect.POSTGRES
import org.jooq.conf.ParamType.INLINED
import org.jooq.impl.DSL
import java.time.Instant.now
import java.util.UUID.randomUUID

class JooqBaseRepositoryBatchingSpec : BehaviorSpec({
    Given("JooqBaseRepository with hacked queryExecutor") {
        val dslContext = DSL.using(POSTGRES)
        val queryExecutor = FakeMemorizingQueryExecutor()

        val addContext = transactionalContext(now())
        val updateContext = transactionalContext(now())

        val repo = DepartmentRepository(queryExecutor, dslContext)

        And("Models of NewState") {
            val depId1 = randomDepartmentId()
            val bossId1 = EmployeeId(randomUUID())
            val dep1 = OwnedDepartment(
                id = depId1,
                name = "store me in the repo",
                boss = bossId1,
                headcount = 1,
                ration = BUBALEH,
                entityState = newState(
                    OwnedDepartmentCreated(
                        departmentId = depId1,
                        name = "store me in the repo",
                        boss = bossId1,
                        headcount = 1,
                        ration = BUBALEH
                    )
                )
            )
            val depId2 = randomDepartmentId()
            val bossId2 = EmployeeId(randomUUID())
            val dep2 = OwnedDepartment(
                id = depId2,
                name = "store me in the repo too",
                boss = bossId2,
                headcount = 1,
                ration = SHAKSHOUKA,
                entityState = newState(
                    OwnedDepartmentCreated(
                        departmentId = depId2,
                        name = "store me in the repo too",
                        boss = bossId1,
                        headcount = 1,
                        ration = SHAKSHOUKA
                    )
                )
            )

            And("Query executor accepts one model as batch insert") {
                queryExecutor.expectQueryFor(
                    DepartmentsRecord().apply {
                        setId(depId1.id)
                        setName(dep1.name)
                        setBoss(dep1.boss!!.id)
                        setHeadcount(dep1.headcount)
                        setRation(dep1.ration.name)
                        setState(DepartmentsState.OWNED)
                        setRecordUpdatedAt(addContext.startedAt)
                        setRecordCreatedAt(addContext.startedAt)
                        setVersion(V1.version)
                    }
                )

                When("Principal saving model") {
                    repo.add(addContext, listOf(dep1))
                    val recordCreatedAt = InstantConverter.instance.to(addContext.startedAt)
                    val boss = requireNotNull(dep1.boss)

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
                            ('${dep1.id().id}',
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

            And("Query executor accepts two models as batch insert") {
                queryExecutor.expectQueryFor(
                    DepartmentsRecord().apply {
                        setId(depId1.id)
                        setName(dep1.name)
                        setBoss(dep1.boss!!.id)
                        setHeadcount(dep1.headcount)
                        setRation(dep1.ration.name)
                        setState(DepartmentsState.OWNED)
                        setRecordUpdatedAt(addContext.startedAt)
                        setRecordCreatedAt(addContext.startedAt)
                        setVersion(V1.version)
                    },
                    DepartmentsRecord().apply {
                        setId(depId2.id)
                        setName(dep2.name)
                        setBoss(dep2.boss!!.id)
                        setHeadcount(dep2.headcount)
                        setRation(dep2.ration.name)
                        setState(DepartmentsState.OWNED)
                        setRecordUpdatedAt(addContext.startedAt)
                        setRecordCreatedAt(addContext.startedAt)
                        setVersion(V1.version)
                    }
                )

                When("Principal saving model") {
                    repo.add(addContext, listOf(dep1, dep2))
                    val recordCreatedAt = InstantConverter.instance.to(addContext.startedAt)
                    val boss1 = requireNotNull(dep1.boss)
                    val boss2 = requireNotNull(dep2.boss)

                    Then(
                        "Query executor should receive two records with RECORD_CREATED_AT and RECORD_UPDATED_AT" +
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
                             ('${dep1.id().id}',
                              'store me in the repo',
                              '${boss1.id}',
                              1,
                              'BUBALEH',
                              'OWNED',
                              timestamp '$recordCreatedAt',
                              timestamp '$recordCreatedAt',
                              1),
                             ('${dep2.id().id}',
                              'store me in the repo too',
                              '${boss2.id}',
                              1,
                              'SHAKSHOUKA',
                              'OWNED',
                              timestamp '$recordCreatedAt',
                              timestamp '$recordCreatedAt',
                              1)
                         """.trim().replace(Regex("\\s+"), " ")
                    }
                }
            }

            And("Query executor accepts one model as batch update") {
                queryExecutor.expectQueryFor(
                    DepartmentsRecord().apply {
                        setId(depId1.id)
                        setName(dep1.name)
                        setBoss(dep1.boss!!.id)
                        setHeadcount(dep1.headcount)
                        setRation(dep1.ration.name)
                        setState(DepartmentsState.OWNED)
                        setRecordUpdatedAt(addContext.startedAt)
                        setRecordCreatedAt(addContext.startedAt)
                        setVersion(V1.version)
                    }
                )
                val addedDep = repo.find(dep1.id()) as OwnedDepartment
                queryExecutor.expectQueryFor(
                    DepartmentsRecord().apply {
                        setId(depId1.id)
                        setName("UPDATE TEST")
                        setBoss(dep1.boss!!.id)
                        setHeadcount(dep1.headcount)
                        setRation(dep1.ration.name)
                        setState(DepartmentsState.OWNED)
                        setRecordUpdatedAt(updateContext.startedAt)
                        setRecordCreatedAt(addContext.startedAt)
                        setVersion(2)
                    }
                )

                When("Principal updating model") {
                    repo.update(updateContext, listOf(addedDep.rename("UPDATE TEST")))
                    val recordUpdatedAt = InstantConverter.instance.to(updateContext.startedAt)
                    val boss = requireNotNull(dep1.boss)

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
                            where ("departments"."id" = '${dep1.id().id}' and "departments"."version" = 1)
                        """.trim().replace(Regex("\\s+"), " ")
                    }
                }
            }

            And("Query executor accepts two models as batch update") {
                queryExecutor.expectQueryFor(
                    DepartmentsRecord().apply {
                        setId(depId1.id)
                        setName(dep1.name)
                        setBoss(dep1.boss!!.id)
                        setHeadcount(dep1.headcount)
                        setRation(dep1.ration.name)
                        setState(DepartmentsState.OWNED)
                        setRecordUpdatedAt(addContext.startedAt)
                        setRecordCreatedAt(addContext.startedAt)
                        setVersion(V1.version)
                    }
                )
                val addedDep1 = repo.find(dep1.id()) as OwnedDepartment
                queryExecutor.expectQueryFor(
                    DepartmentsRecord().apply {
                        setId(depId2.id)
                        setName(dep2.name)
                        setBoss(dep2.boss!!.id)
                        setHeadcount(dep2.headcount)
                        setRation(dep2.ration.name)
                        setState(DepartmentsState.OWNED)
                        setRecordUpdatedAt(addContext.startedAt)
                        setRecordCreatedAt(addContext.startedAt)
                        setVersion(V1.version)
                    }
                )
                val addedDep2 = repo.find(dep2.id()) as OwnedDepartment
                queryExecutor.expectQueryFor(
                    DepartmentsRecord().apply {
                        setId(depId1.id)
                        setName("UPDATE TEST")
                        setBoss(dep1.boss!!.id)
                        setHeadcount(dep1.headcount)
                        setRation(dep1.ration.name)
                        setState(DepartmentsState.OWNED)
                        setRecordUpdatedAt(updateContext.startedAt)
                        setRecordCreatedAt(addContext.startedAt)
                        setVersion(2)
                    },
                    DepartmentsRecord().apply {
                        setId(depId2.id)
                        setName("UPDATE TEST 2")
                        setBoss(dep2.boss!!.id)
                        setHeadcount(dep2.headcount)
                        setRation(dep2.ration.name)
                        setState(DepartmentsState.OWNED)
                        setRecordUpdatedAt(updateContext.startedAt)
                        setRecordCreatedAt(addContext.startedAt)
                        setVersion(2)
                    }
                )

                When("Principal updating model") {
                    repo.update(
                        updateContext,
                        listOf(addedDep1.rename("UPDATE TEST"), addedDep2.rename("UPDATE TEST 2"))
                    )
                    val recordUpdatedAt = InstantConverter.instance.to(updateContext.startedAt)
                    val boss1 = requireNotNull(dep1.boss)
                    val boss2 = requireNotNull(dep2.boss)

                    Then(
                        "Query executor should receive two records with RECORD_CREATED_AT and RECORD_UPDATED_AT" +
                            " matching context.startedAt and V2"
                    ) {
                        val update = queryExecutor.lastExecution.shouldBeTypeOf<StoreExecuted>()
                        update.jooqQuery.getSQL(INLINED) shouldBe """
                            update "departments" as "T" 
                            set ("name", "boss", "headcount", "ration", "state", "record_updated_at", "version") 
                            = row
                            ("U"."name",
                            "U"."boss",
                            "U"."headcount",
                            "U"."ration",
                            "U"."state",
                            "U"."record_updated_at",
                            "U"."version")
                            
                            from (values 
                                (cast('${dep1.id().id}' as uuid),
                                cast('UPDATE TEST' as text),
                                cast('${boss1.id}' as uuid),
                                cast(1 as int),
                                cast('BUBALEH' as text),
                                cast('OWNED' as departments_state),
                                cast(timestamp '$recordUpdatedAt' as timestamp(6)),
                                cast(2 as bigint)),
                                    
                                (cast('${dep2.id().id}' as uuid),
                                cast('UPDATE TEST 2' as text),
                                cast('${boss2.id}' as uuid),
                                cast(1 as int),
                                cast('SHAKSHOUKA' as text),
                                cast('OWNED' as departments_state),
                                cast(timestamp '$recordUpdatedAt' as timestamp(6)),
                                cast(2 as bigint)))
                                
                            as "U"
                            ("id",
                            "name",
                            "boss",
                            "headcount",
                            "ration",
                            "state",
                            "record_updated_at",
                            "version")
                            
                            where (cast("U"."id" as uuid) = cast("T"."id" as uuid)
                                and cast("U"."version" as bigint) = (cast("T"."version" as bigint) + 1))
                        """.trim().replace(Regex("\\s+"), " ")
                    }
                }
            }
        }
    }
})
