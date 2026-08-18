package com.razz.eva.tracing

import com.razz.eva.tracing.QueryNaming.operationName
import com.razz.eva.tracing.QueryNaming.queryTarget
import com.razz.eva.tracing.QueryNaming.spanName
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jooq.Record
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl

private val events = object : TableImpl<Record>(DSL.name("model_events")) {
    val ID = createField(DSL.name("id"), SQLDataType.UUID)!!
}

class QueryNamingSpec : FunSpec({

    val ctx = DSL.using(POSTGRES)
    val nullUuid = DSL.inline(null, SQLDataType.UUID)

    test("names a select after the table") {
        spanName(ctx.selectFrom(events)) shouldBe "SELECT model_events"
    }

    test("names a dsl delete") {
        val query = ctx.deleteFrom(events).where(events.ID.isNotNull)
        operationName(query) shouldBe "DELETE"
        queryTarget(query) shouldBe "model_events"
    }

    test("names a dsl insert") {
        val query = ctx.insertInto(events).columns(events.ID).values(nullUuid)
        spanName(query) shouldBe "INSERT model_events"
    }

    test("names a dsl update") {
        spanName(ctx.update(events).set(events.ID, nullUuid)) shouldBe "UPDATE model_events"
    }

    test("names a query object insert") {
        spanName(ctx.insertQuery(events)) shouldBe "INSERT model_events"
    }

    test("names a query object delete") {
        spanName(ctx.deleteQuery(events)) shouldBe "DELETE model_events"
    }

    test("a merge upsert has no target") {
        val query = ctx.mergeInto(events, events.ID).values(nullUuid)
        operationName(query) shouldBe "MERGE"
        queryTarget(query) shouldBe null
        spanName(query) shouldBe "MERGE"
    }

    test("an unknown query falls back") {
        spanName(null) shouldBe "QUERY"
    }
})
