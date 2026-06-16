package com.razz.eva.repository

import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.SelectExecuted
import com.razz.eva.test.schema.enums.DepartmentsState
import com.razz.eva.test.schema.tables.records.DepartmentsRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import org.jooq.SQLDialect.POSTGRES
import org.jooq.conf.ParamType.INLINED
import org.jooq.impl.DSL
import java.util.UUID.randomUUID

class JooqBaseRepositoryFindOneWhereSpec : BehaviorSpec({
    Given("JooqBaseRepository with hacked queryExecutor") {
        val dslContext = DSL.using(POSTGRES)
        val queryExecutor = FakeMemorizingQueryExecutor()

        val repo = DepartmentRepository(queryExecutor, dslContext)

        When("Query executor returns a single record for findOneWhere") {
            val depId = DepartmentId(randomUUID())
            val bossId = EmployeeId(randomUUID())
            val name = "the only one"
            queryExecutor.expectQueryFor(
                DepartmentsRecord().apply {
                    setId(depId.id)
                    setName(name)
                    setBoss(bossId.id)
                    setHeadcount(1)
                    setRation(BUBALEH.name)
                    setState(DepartmentsState.OWNED)
                    setVersion(1)
                },
            )

            val found = repo.findByName(name)

            Then("findOneWhere returns the mapped model") {
                val owned = found.shouldBeTypeOf<OwnedDepartment>()
                owned.id() shouldBe depId
                owned.name shouldBe name
                owned.boss shouldBe bossId
            }

            Then("Query executor receives a select with limit 2") {
                val select = queryExecutor.lastExecution.shouldBeTypeOf<SelectExecuted>()
                select.jooqQuery.getSQL(INLINED) shouldBe """
                    select "departments"."id",
                           "departments"."name",
                           "departments"."boss",
                           "departments"."headcount",
                           "departments"."ration",
                           "departments"."state",
                           "departments"."record_updated_at",
                           "departments"."record_created_at",
                           "departments"."version"
                    from "departments"
                    where "departments"."name" = '$name'
                    fetch next 2 rows only
                """.trim().replace(Regex("\\s+"), " ")
            }
        }

        When("Query executor returns more than one record for findOneWhere") {
            val name = "duplicate name"
            queryExecutor.expectQueryFor(
                DepartmentsRecord().apply {
                    setId(randomUUID())
                    setName(name)
                    setBoss(randomUUID())
                    setHeadcount(1)
                    setRation(BUBALEH.name)
                    setState(DepartmentsState.OWNED)
                    setVersion(1)
                },
                DepartmentsRecord().apply {
                    setId(randomUUID())
                    setName(name)
                    setBoss(randomUUID())
                    setHeadcount(2)
                    setRation(BUBALEH.name)
                    setState(DepartmentsState.OWNED)
                    setVersion(1)
                },
            )

            Then("findOneWhere throws JooqQueryException") {
                val ex = shouldThrow<JooqQueryException> {
                    repo.findByName(name)
                }
                ex.message shouldContain "Found more than one record"
                ex.records.size shouldBe 2
            }
        }
    }
})
