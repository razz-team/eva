package com.razz.eva.repository

import com.razz.eva.domain.Bubaleh
import com.razz.eva.domain.BubalehFixtures.aServedBubaleh
import com.razz.eva.domain.BubalehId
import com.razz.eva.test.schema.enums.BubalehsState.SERVED
import com.razz.eva.test.schema.tables.Bubalehs.BUBALEHS
import com.razz.eva.test.schema.tables.records.BubalehsRecord
import com.razz.eva.paging.BasicPagedList
import com.razz.eva.paging.ModelOffset
import com.razz.eva.paging.Size
import com.razz.eva.paging.TimestampPage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.jooq.SQLDialect.POSTGRES
import org.jooq.TableField
import org.jooq.conf.ParamType.INLINED
import org.jooq.impl.DSL
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.*

class PagingStrategySpec : BehaviorSpec({

    class ServedBubalehPagingStrategy : PagingStrategy<UUID, BubalehId, Bubaleh, Bubaleh.Served, BubalehsRecord>(
        Bubaleh.Served::class
    ) {
        override fun tableTimestamp(): TableField<BubalehsRecord, Instant> = BUBALEHS.PRODUCED_ON
        override fun tableId(): TableField<BubalehsRecord, UUID> = BUBALEHS.ID
        override fun tableOffset(modelOffset: ModelOffset) = UUID.fromString(modelOffset)
        override fun modelTimestamp(model: Bubaleh.Served) = model.producedOn
        override fun modelOffset(model: Bubaleh.Served) = model.id().stringValue()
    }

    val clock = Clock.systemUTC()

    Given("Page size") {
        val size = Size(1)

        And("Paging strategy") {
            val strategy = ServedBubalehPagingStrategy()

            And("First page") {
                val page = TimestampPage.First(size)

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
                val maxTimestamp = clock.instant()
                val page = TimestampPage.Next(
                    maxTimestamp = maxTimestamp,
                    modelIdOffset = "a5e15308-3a8d-462b-b96c-6f1137e30f0d",
                    size = size
                )

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
                            and ("bubalehs"."produced_on" < timestamp '${Timestamp.from(maxTimestamp)}' 
                            or ("bubalehs"."produced_on" = timestamp '${Timestamp.from(maxTimestamp)}' 
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
                    val pagedList = strategy.pagedList(result, size)

                    Then("Paged list is correct") {
                        pagedList shouldBe BasicPagedList(
                            result,
                            TimestampPage.Next(
                                maxTimestamp = model.producedOn,
                                modelIdOffset = model.id().stringValue(),
                                size = size
                            )
                        )
                    }
                }
            }
        }
    }
})
