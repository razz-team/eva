package com.razz.eva.repository

import com.razz.eva.test.repository.RepositorySpec
import com.razz.eva.test.schema.Tables.DEPARTMENTS
import com.razz.eva.test.schema.enums.DepartmentsState.OWNED
import io.kotest.matchers.shouldBe
import java.util.UUID
import org.jooq.impl.DSL

class QueryExecutorReturningSpec : RepositorySpec(TestEvaRepositoryHelper, {

    fun insertQuery(id: UUID) = dslContext.insertQuery(DEPARTMENTS).apply {
        addValue(DEPARTMENTS.ID, id)
        addValue(DEPARTMENTS.NAME, "dep-$id")
        addValue(DEPARTMENTS.BOSS, UUID.randomUUID())
        addValue(DEPARTMENTS.HEADCOUNT, 1)
        addValue(DEPARTMENTS.RATION, "BUBALEH")
        addValue(DEPARTMENTS.STATE, OWNED)
        addValue(DEPARTMENTS.RECORD_UPDATED_AT, now)
        addValue(DEPARTMENTS.RECORD_CREATED_AT, now)
        addValue(DEPARTMENTS.VERSION, 1L)
    }

    Given("Query executor over a real database") {

        When("Principal inserts without explicit returning fields") {
            val id = UUID.randomUUID()
            val stored = inTransaction {
                executor.executeStore(dslContext, insertQuery(id), DEPARTMENTS)
            }

            Then("All columns are populated") {
                val record = stored.single()
                record.get(DEPARTMENTS.ID) shouldBe id
                record.get(DEPARTMENTS.NAME) shouldBe "dep-$id"
                record.get(DEPARTMENTS.STATE) shouldBe OWNED
                record.get(DEPARTMENTS.RECORD_UPDATED_AT) shouldBe now
                record.get(DEPARTMENTS.VERSION) shouldBe 1L
            }
        }

        When("Principal inserts with explicit returning fields") {
            val id = UUID.randomUUID()
            val stored = inTransaction {
                executor.executeStore(
                    dslContext,
                    insertQuery(id),
                    DEPARTMENTS,
                    listOf(DEPARTMENTS.ID, DEPARTMENTS.STATE, DEPARTMENTS.RECORD_UPDATED_AT),
                )
            }

            Then("Only the requested fields are populated") {
                val record = stored.single()
                record.get(DEPARTMENTS.ID) shouldBe id
                record.get(DEPARTMENTS.STATE) shouldBe OWNED
                record.get(DEPARTMENTS.RECORD_UPDATED_AT) shouldBe now
                record.get(DEPARTMENTS.NAME) shouldBe null
                record.get(DEPARTMENTS.VERSION) shouldBe null
            }
        }

        When("Principal inserts two rows with explicit returning fields") {
            val firstId = UUID.randomUUID()
            val secondId = UUID.randomUUID()
            val insert = insertQuery(firstId).apply {
                newRecord()
                addValue(DEPARTMENTS.ID, secondId)
                addValue(DEPARTMENTS.NAME, "dep-$secondId")
                addValue(DEPARTMENTS.BOSS, UUID.randomUUID())
                addValue(DEPARTMENTS.HEADCOUNT, 2)
                addValue(DEPARTMENTS.RATION, "SHAKSHOUKA")
                addValue(DEPARTMENTS.STATE, OWNED)
                addValue(DEPARTMENTS.RECORD_UPDATED_AT, now)
                addValue(DEPARTMENTS.RECORD_CREATED_AT, now)
                addValue(DEPARTMENTS.VERSION, 1L)
            }
            val stored = inTransaction {
                executor.executeStore(dslContext, insert, DEPARTMENTS, listOf(DEPARTMENTS.ID))
            }

            Then("Each returned record carries only its id") {
                stored.map { it.get(DEPARTMENTS.ID) } shouldBe listOf(firstId, secondId)
                stored.map { it.get(DEPARTMENTS.NAME) } shouldBe listOf(null, null)
            }
        }

        When("Principal updates an aliased table passing fields of the unaliased table") {
            val id = UUID.randomUUID()
            inTransaction {
                executor.executeStore(dslContext, insertQuery(id), DEPARTMENTS)
            }
            val aliased = DEPARTMENTS.`as`("t")
            val aliasedId = requireNotNull(aliased.field(DEPARTMENTS.ID))
            val aliasedHeadcount = requireNotNull(aliased.field(DEPARTMENTS.HEADCOUNT))
            val update = dslContext.updateQuery(aliased).apply {
                addValue(aliasedHeadcount, 42)
                addConditions(aliasedId.eq(id))
            }
            val stored = inTransaction {
                executor.executeStore(
                    dslContext,
                    update,
                    DEPARTMENTS,
                    listOf(DEPARTMENTS.ID, DEPARTMENTS.HEADCOUNT),
                )
            }

            Then("Returning fields are requalified with the alias and the statement executes") {
                val record = stored.single()
                record.get(DEPARTMENTS.ID) shouldBe id
                record.get(DEPARTMENTS.HEADCOUNT) shouldBe 42
                record.get(DEPARTMENTS.NAME) shouldBe null
            }
        }

        When("Principal inserts with an expression aliased to a column name") {
            val id = UUID.randomUUID()
            val stored = inTransaction {
                executor.executeStore(
                    dslContext,
                    insertQuery(id),
                    DEPARTMENTS,
                    listOf(DEPARTMENTS.ID, DSL.upper(DEPARTMENTS.NAME).`as`("name")),
                )
            }

            Then("Expression value populates the column matched by name") {
                stored.single().get(DEPARTMENTS.NAME) shouldBe "dep-$id".uppercase()
            }
        }
    }
})
