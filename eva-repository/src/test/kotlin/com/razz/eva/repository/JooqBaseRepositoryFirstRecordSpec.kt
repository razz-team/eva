package com.razz.eva.repository

import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.SelectExecuted
import com.razz.eva.test.schema.tables.records.TagRecord
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jooq.SQLDialect.POSTGRES
import org.jooq.conf.ParamType.INLINED
import org.jooq.impl.DSL
import java.util.UUID.randomUUID

class JooqBaseRepositoryFirstRecordSpec : BehaviorSpec({
    Given("JooqBaseEntityRepository with hacked queryExecutor") {
        val dslContext = DSL.using(POSTGRES)
        val queryExecutor = FakeMemorizingQueryExecutor()

        val repo = TagRepository(queryExecutor, dslContext)

        When("Query executor returns multiple records for firstRecord") {
            val subjectId = randomUUID()
            queryExecutor.expectQueryFor(
                TagRecord(subjectId, "first", "earliest"),
                TagRecord(subjectId, "second", "later"),
            )

            val found = repo.findFirstBySubject(subjectId)

            Then("firstRecord returns the first record without throwing on extras") {
                found shouldNotBe null
                found?.subjectId shouldBe subjectId
                found?.name shouldBe "first"
                found?.value shouldBe "earliest"
            }

            Then("Query executor receives a select with limit 1") {
                val select = queryExecutor.lastExecution.shouldBeTypeOf<SelectExecuted>()
                select.jooqQuery.getSQL(INLINED) shouldBe """
                    select "tag"."subject_id", "tag"."name", "tag"."value"
                    from "tag"
                    where "tag"."subject_id" = cast('$subjectId' as uuid)
                    order by "tag"."name" asc
                    fetch next 1 rows only
                """.trim().replace(Regex("\\s+"), " ")
            }
        }
    }
})
