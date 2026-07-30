package com.razz.eva.repository

import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.SelectExecuted
import com.razz.eva.test.schema.tables.records.DepartmentsRecord
import com.razz.eva.test.schema.tables.records.TagRecord
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jooq.SQLDialect.POSTGRES
import org.jooq.conf.ParamType.INLINED
import org.jooq.impl.DSL
import java.util.UUID.randomUUID

class JooqBaseRepositoryCountByGroupSpec : BehaviorSpec({
    Given("JooqBaseModelRepository with hacked queryExecutor") {
        val dslContext = DSL.using(POSTGRES)
        val queryExecutor = FakeMemorizingQueryExecutor()

        val repo = DepartmentRepository(queryExecutor, dslContext)

        When("Principal executes count by group") {
            val firstId = DepartmentId(randomUUID())
            val secondId = DepartmentId(randomUUID())
            queryExecutor.expectQueryFor(
                DepartmentsRecord().apply {
                    setRation(BUBALEH.name)
                },
            )
            repo.countByRation(listOf(firstId, secondId))

            Then(
                "Query executor should receive grouped count select",
            ) {
                val select = queryExecutor.lastExecution.shouldBeTypeOf<SelectExecuted>()
                select.jooqQuery.getSQL(INLINED) shouldBe """
                      select "departments"."ration", count(*)
                      from "departments"
                      where "departments"."id" in (cast('${firstId.id}' as uuid),
                        cast('${secondId.id}' as uuid))
                      group by "departments"."ration"
                 """.trim().replace(Regex("\\s+"), " ")
            }
        }
    }

    Given("JooqBaseEntityRepository with hacked queryExecutor") {
        val dslContext = DSL.using(POSTGRES)
        val queryExecutor = FakeMemorizingQueryExecutor()

        val repo = TagRepository(queryExecutor, dslContext)

        When("Principal executes count by group") {
            val firstSubject = randomUUID()
            val secondSubject = randomUUID()
            queryExecutor.expectQueryFor(TagRecord(firstSubject, "priority", "high"))
            repo.countBySubjects(listOf(firstSubject, secondSubject))

            Then(
                "Query executor should receive grouped count select",
            ) {
                val select = queryExecutor.lastExecution.shouldBeTypeOf<SelectExecuted>()
                select.jooqQuery.getSQL(INLINED) shouldBe """
                      select "tag"."subject_id", count(*)
                      from "tag"
                      where "tag"."subject_id" in (cast('$firstSubject' as uuid),
                        cast('$secondSubject' as uuid))
                      group by "tag"."subject_id"
                 """.trim().replace(Regex("\\s+"), " ")
            }
        }
    }
})
