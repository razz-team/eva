package com.razz.eva.repository

import com.razz.eva.domain.EmployeeId
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor
import com.razz.eva.persistence.executor.FakeMemorizingQueryExecutor.ExecutionStep.SelectExecuted
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jooq.SQLDialect.POSTGRES
import org.jooq.conf.ParamType.INLINED
import org.jooq.impl.DSL
import java.util.UUID.randomUUID

class JooqBaseRepositoryExistsPositiveSpec : BehaviorSpec({
    Given("JooqBaseRepository with hacked queryExecutor") {
        val dslContext = DSL.using(POSTGRES)
        val queryExecutor = FakeMemorizingQueryExecutor()

        val repo = DepartmentRepository(queryExecutor, dslContext)

        When("Principal execute exists") {
            val bossId = EmployeeId(randomUUID())
            repo.existsFor(bossId)

            Then(
                "Query executor should receive exists",
            ) {
                val exists = queryExecutor.lastExecution.shouldBeTypeOf<SelectExecuted>()
                exists.jooqQuery.getSQL(INLINED) shouldBe """
                      select 1 as "one" 
                      where exists 
                      (select 1 as "one" 
                        from "departments" 
                        where "departments"."boss" = cast('${bossId.id}' as uuid))     
                 """.trim().replace(Regex("\\s+"), " ")
            }
        }
    }
})
