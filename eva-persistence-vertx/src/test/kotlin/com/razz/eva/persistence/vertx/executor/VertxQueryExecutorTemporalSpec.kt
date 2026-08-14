package com.razz.eva.persistence.vertx.executor

import com.razz.eva.persistence.vertx.PgPoolConnectionProvider
import com.razz.eva.persistence.vertx.VertxConnectionElement
import com.razz.eva.persistence.vertx.VertxTransactionManager
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.vertx.core.Future.succeededFuture
import io.vertx.pgclient.PgConnection
import io.vertx.sqlclient.PreparedQuery
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.impl.ListTuple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.Condition
import org.jooq.Converter
import org.jooq.Record
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.util.function.Function

/** Mirrors com.razz.jooq.converter.LocalDateConverter, which eva-persistence-vertx cannot depend on. */
private class SqlDateConverter : Converter<Date, LocalDate> {
    override fun from(databaseObject: Date?): LocalDate? = databaseObject?.toLocalDate()
    override fun to(userObject: LocalDate?): Date? = userObject?.let(Date::valueOf)
    override fun fromType(): Class<Date> = Date::class.java
    override fun toType(): Class<LocalDate> = LocalDate::class.java
}

/** Mirrors com.razz.jooq.converter.InstantConverter. */
private class SqlTimestampConverter : Converter<Timestamp, Instant> {
    override fun from(databaseObject: Timestamp?): Instant? = databaseObject?.toLocalDateTime()?.toInstant(UTC)
    override fun to(userObject: Instant?): Timestamp? =
        userObject?.let { Timestamp.valueOf(LocalDateTime.ofInstant(it, UTC)) }
    override fun fromType(): Class<Timestamp> = Timestamp::class.java
    override fun toType(): Class<Instant> = Instant::class.java
}

private val temporalTable = object : TableImpl<Record>(DSL.name("temporal_test")) {
    val DAY = createField(DSL.name("day"), SQLDataType.DATE.asConvertedDataType(SqlDateConverter()))!!
    val AT = createField(DSL.name("at"), SQLDataType.TIMESTAMP.asConvertedDataType(SqlTimestampConverter()))!!
}

class VertxQueryExecutorTemporalSpec : ShouldSpec({

    val dslContext = DSL.using(POSTGRES)
    val connectionProvider = mockk<PgPoolConnectionProvider>(relaxed = true)
    val transactionManager = spyk(VertxTransactionManager(connectionProvider, connectionProvider))
    val executor = VertxQueryExecutor(transactionManager)

    val paramsSlot = slot<ListTuple>()

    val preparedQueryMock = mockk<PreparedQuery<RowSet<Row>>> {
        every { mapping(any<Function<Row, Any>>()) } answers {
            mockk {
                every { execute(capture(paramsSlot)) } returns succeededFuture(
                    mockk {
                        every { iterator() } answers {
                            mockk { every { hasNext() } returns false }
                        }
                        every { size() } returns 0
                    },
                )
            }
        }
    }

    val connection = mockk<PgConnection>(relaxed = true) {
        every { preparedQuery(any()) } answers { preparedQueryMock }
    }
    coEvery { connectionProvider.acquire() } coAnswers { connection }

    suspend fun boundValue(condition: Condition): Any? {
        clearMocks(connection, answers = false)
        every { connection.preparedQuery(any()) } answers { preparedQueryMock }
        withContext(Dispatchers.IO + VertxConnectionElement(connection)) {
            executor.executeSelect(dslContext, dslContext.selectFrom(temporalTable).where(condition), temporalTable)
        }
        return paramsSlot.captured.getValue(0)
    }

    should("bind a date array as a LocalDate array") {
        val days = listOf(
            LocalDate.parse("2026-08-10"),
            LocalDate.parse("2026-08-11"),
            LocalDate.parse("2026-08-12"),
            LocalDate.parse("2026-08-13"),
        )
        val bound = boundValue(temporalTable.DAY.eq(DSL.any(*days.toTypedArray())))
        bound!!::class.java shouldBe Array<LocalDate>::class.java
        (bound as Array<*>).toList() shouldBe days
    }

    should("bind a timestamp array as a LocalDateTime array") {
        val moments = listOf(
            Instant.parse("2026-08-10T10:15:30Z"),
            Instant.parse("2026-08-11T10:15:30Z"),
            Instant.parse("2026-08-12T10:15:30Z"),
            Instant.parse("2026-08-13T10:15:30Z"),
        )
        val bound = boundValue(temporalTable.AT.eq(DSL.any(*moments.toTypedArray())))
        bound!!::class.java shouldBe Array<LocalDateTime>::class.java
        (bound as Array<*>).toList() shouldBe moments.map { LocalDateTime.ofInstant(it, UTC) }
    }

    should("keep binding a single date as a LocalDate") {
        val day = LocalDate.parse("2026-08-13")
        boundValue(temporalTable.DAY.eq(day)) shouldBe day
    }

    should("keep binding a single instant as a LocalDateTime") {
        val moment = Instant.parse("2026-08-13T10:15:30Z")
        boundValue(temporalTable.AT.eq(moment)) shouldBe LocalDateTime.ofInstant(moment, UTC)
    }
})
