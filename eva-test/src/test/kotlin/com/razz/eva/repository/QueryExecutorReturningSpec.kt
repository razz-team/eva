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

        When("Principal inserts returning plain columns, an expression and a column aliased outside the table") {
            val id = UUID.randomUUID()
            val stored = inTransaction {
                executor.executeStore(
                    dslContext,
                    insertQuery(id),
                    DEPARTMENTS,
                    listOf(
                        DEPARTMENTS.ID,
                        DEPARTMENTS.STATE,
                        DEPARTMENTS.RECORD_UPDATED_AT,
                        DSL.upper(DEPARTMENTS.NAME).`as`("name"),
                        DEPARTMENTS.RATION.`as`("meal"),
                    ),
                )
            }

            Then("Plain columns and the name-matched expression are populated, the rest stays null") {
                val record = stored.single()
                record.get(DEPARTMENTS.ID) shouldBe id
                record.get(DEPARTMENTS.STATE) shouldBe OWNED
                record.get(DEPARTMENTS.RECORD_UPDATED_AT) shouldBe now
                record.get(DEPARTMENTS.NAME) shouldBe "dep-$id".uppercase()
                record.get(DEPARTMENTS.RATION) shouldBe null
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

        When("Principal updates an aliased table") {
            val id = UUID.randomUUID()
            inTransaction {
                executor.executeStore(dslContext, insertQuery(id), DEPARTMENTS)
            }
            val aliased = DEPARTMENTS.`as`("t")
            val aliasedId = requireNotNull(aliased.field(DEPARTMENTS.ID))
            val aliasedHeadcount = requireNotNull(aliased.field(DEPARTMENTS.HEADCOUNT))
            fun updateQuery(headcount: Int) = dslContext.updateQuery(aliased).apply {
                addValue(aliasedHeadcount, headcount)
                addConditions(aliasedId.eq(id))
            }
            val unaliasedFields = inTransaction {
                executor.executeStore(
                    dslContext,
                    updateQuery(42),
                    DEPARTMENTS,
                    listOf(DEPARTMENTS.ID, DEPARTMENTS.HEADCOUNT),
                )
            }
            val fullRow = inTransaction {
                executor.executeStore(dslContext, updateQuery(7), DEPARTMENTS)
            }
            val aliasedFields = inTransaction {
                executor.executeStore(dslContext, updateQuery(13), DEPARTMENTS, listOf(aliasedId, aliasedHeadcount))
            }

            Then("Fields of the unaliased table are requalified with the alias and the statement executes") {
                val record = unaliasedFields.single()
                record.get(DEPARTMENTS.ID) shouldBe id
                record.get(DEPARTMENTS.HEADCOUNT) shouldBe 42
                record.get(DEPARTMENTS.NAME) shouldBe null
            }
            And("Without explicit fields the full row comes back through the alias-qualified returning") {
                val record = fullRow.single()
                record.get(DEPARTMENTS.ID) shouldBe id
                record.get(DEPARTMENTS.HEADCOUNT) shouldBe 7
                record.get(DEPARTMENTS.NAME) shouldBe "dep-$id"
                record.get(DEPARTMENTS.VERSION) shouldBe 1L
            }
            And("Fields of the aliased table are populated on the unaliased table record") {
                val record = aliasedFields.single()
                record.get(DEPARTMENTS.ID) shouldBe id
                record.get(DEPARTMENTS.HEADCOUNT) shouldBe 13
                record.get(DEPARTMENTS.NAME) shouldBe null
            }
        }
    }
})
