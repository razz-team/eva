package com.razz.eva.repository

import com.razz.eva.domain.Bubaleh
import com.razz.eva.domain.BubalehBottleVol
import com.razz.eva.domain.BubalehFixtures.aConsumedBubaleh
import com.razz.eva.domain.BubalehFixtures.aServedBubaleh
import com.razz.eva.domain.BubalehId
import com.razz.eva.domain.BubalehState
import com.razz.eva.domain.BubalehTaste
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.EntityState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.paging.BasicPagedList
import com.razz.eva.paging.Offset
import com.razz.eva.paging.Page
import com.razz.eva.paging.Page.Factory.firstPage
import com.razz.eva.paging.Size
import com.razz.eva.test.schema.enums.BubalehsState.CONSUMED
import com.razz.eva.test.schema.enums.BubalehsState.SERVED
import com.razz.eva.test.schema.tables.Bubalehs.BUBALEHS
import com.razz.eva.test.schema.tables.records.BubalehsRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.jooq.SQLDialect.POSTGRES
import org.jooq.TableField
import org.jooq.conf.ParamType.INLINED
import org.jooq.impl.DSL
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.util.UUID

class PagingStrategySpec : BehaviorSpec({

    class ServedBubalehPagingStrategy : ModelPagingStrategy<
        UUID,
        BubalehId,
        Bubaleh,
        Bubaleh.Served,
        Instant,
        BubalehsRecord
        >(
        Bubaleh.Served::class
    ) {
        override fun tableOrdering(): TableField<BubalehsRecord, Instant> = BUBALEHS.PRODUCED_ON
        override fun tableId(): TableField<BubalehsRecord, UUID> = BUBALEHS.ID
        override fun tableOffset(offset: Offset) = UUID.fromString(offset)
        override fun ordering(data: Bubaleh.Served) = data.producedOn
        override fun offset(data: Bubaleh.Served) = data.id().stringValue()
        override fun failOnWrongModel(): Boolean = true
    }

    Given("Page size") {
        val size = Size(1)

        And("Paging strategy") {
            val strategy = ServedBubalehPagingStrategy()

            And("First page") {
                val page = firstPage<Instant>(size)

                And("Selection step") {
                    val step = DSL.using(POSTGRES)
                        .selectFrom(BUBALEHS)
                        .where(BUBALEHS.STATE.eq(SERVED))

                    When("Principal generates paging select query") {
                        val selectQuery = strategy.select(step, page)

                        Then("Select query is correct") {
                            selectQuery.getSQL(INLINED) shouldBe """
                            select 
                            "bubalehs"."id", 
                            "bubalehs"."employee_id", 
                            "bubalehs"."state", 
                            "bubalehs"."taste", 
                            "bubalehs"."produced_on", 
                            "bubalehs"."volume", 
                            "bubalehs"."record_updated_at", 
                            "bubalehs"."record_created_at", 
                            "bubalehs"."version" 
                            from "bubalehs" 
                            where "bubalehs"."state" = cast('SERVED' as "bubalehs_state")
                            order by "bubalehs"."produced_on" desc, "bubalehs"."id" 
                            fetch next 1 rows only
                            """.trimIndent().replace(Regex("\\s+"), " ")
                        }
                    }
                }
            }

            And("Next page") {
                val maxTimestamp = Instant.parse("2023-06-20T15:54:30.123Z")
                val page = Page.Next(
                    maxOrdering = maxTimestamp,
                    offset = "a5e15308-3a8d-462b-b96c-6f1137e30f0d",
                    size = size
                )
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(UTC)
                val renderedTimestamp = formatter.format(maxTimestamp)

                And("Selection step") {
                    val step = DSL.using(POSTGRES)
                        .selectFrom(BUBALEHS)
                        .where(BUBALEHS.STATE.eq(SERVED))

                    When("Principal generates paging select query") {
                        val selectQuery = strategy.select(step, page)

                        Then("Select query is correct") {
                            selectQuery.getSQL(INLINED) shouldBe """
                            select 
                            "bubalehs"."id", 
                            "bubalehs"."employee_id", 
                            "bubalehs"."state", 
                            "bubalehs"."taste", 
                            "bubalehs"."produced_on", 
                            "bubalehs"."volume", 
                            "bubalehs"."record_updated_at", 
                            "bubalehs"."record_created_at", 
                            "bubalehs"."version" 
                            from "bubalehs" 
                            where 
                            ("bubalehs"."state" = cast('SERVED' as "bubalehs_state")
                            and ("bubalehs"."produced_on" < timestamp '$renderedTimestamp' 
                            or ("bubalehs"."produced_on" = timestamp '$renderedTimestamp' 
                            and "bubalehs"."id" > 'a5e15308-3a8d-462b-b96c-6f1137e30f0d'))) 
                            order by "bubalehs"."produced_on" desc, "bubalehs"."id" 
                            fetch next 1 rows only
                            """.trimIndent().replace(Regex("\\s+"), " ")
                        }
                    }
                }
            }

            And("Selection result") {
                val model = aServedBubaleh()
                val result = listOf(model)

                When("Principal wraps selection result to paged list") {
                    val pagedList = strategy.pagedList(listOf(model).map(::record), ::model, size)

                    Then("Paged list is correct") {
                        pagedList shouldBe BasicPagedList(
                            result,
                            Page.Next(
                                maxOrdering = model.producedOn,
                                offset = model.id().stringValue(),
                                size = size
                            )
                        )
                    }
                }
            }

            And("Selection has models of different type") {
                val model = aServedBubaleh()
                val anotherModel = aConsumedBubaleh(
                    id = BubalehId.fromString("401b9b20-db2d-463b-a4ed-0840a18dcb52")
                )
                val result = listOf(anotherModel, model).map(::record)

                When("Principal wraps selection result to paged list") {
                    val attempt = { strategy.pagedList(result, ::model, size) }

                    Then("Exception is returned") {
                        val ex = shouldThrow<IllegalStateException>(attempt)
                        ex.message shouldBe "Model 401b9b20-db2d-463b-a4ed-0840a18dcb52 has Consumed type, " +
                            "while it should have Served type"
                    }
                }
            }
        }
    }
})

private fun record(model: Bubaleh) = BubalehsRecord().apply {
    setId(model.id().id)
    employeeId = model.employeeId.id
    taste = model.taste.name
    producedOn = model.producedOn
    volume = model.volume.name
    setState(
        when (model.state()) {
            BubalehState.SERVED -> SERVED
            BubalehState.CONSUMED -> CONSUMED
        }
    )
}

fun model(record: BubalehsRecord): Bubaleh {
    @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
    return when (record.state) {
        SERVED -> Bubaleh.Served(
            BubalehId(record.id),
            EmployeeId(record.employeeId),
            BubalehTaste.valueOf(record.taste),
            record.producedOn,
            BubalehBottleVol.valueOf(record.volume),
            persistentState(V1),
        )
        CONSUMED -> Bubaleh.Consumed(
            BubalehId(record.id),
            EmployeeId(record.employeeId),
            BubalehTaste.valueOf(record.taste),
            record.producedOn,
            BubalehBottleVol.valueOf(record.volume),
            persistentState(V1),
        )
    }
}
