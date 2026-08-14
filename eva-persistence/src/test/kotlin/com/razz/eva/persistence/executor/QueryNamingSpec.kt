package com.razz.eva.persistence.executor

import com.razz.eva.persistence.executor.QueryExecutor.Companion.operationName
import com.razz.eva.persistence.executor.QueryExecutor.Companion.queryTarget
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

/**
 * The DSL forms matter as much as the query objects: `deleteFrom(t)` is a `QOM.Delete` rather than a
 * `DeleteQuery`, so matching only the query-object types named every such span after nothing at all.
 */
class QueryNamingSpec : FunSpec({

    val ctx = DSL.using(POSTGRES)

    test("names a dsl delete") {
        val query = ctx.deleteFrom(events).where(events.ID.isNotNull)
        operationName(query) shouldBe "DELETE"
        queryTarget(query) shouldBe "model_events"
    }

    test("names a dsl insert") {
        val query = ctx.insertInto(events).columns(events.ID).values(DSL.inline(null, SQLDataType.UUID))
        operationName(query) shouldBe "INSERT"
        queryTarget(query) shouldBe "model_events"
    }

    test("names a dsl update") {
        val query = ctx.update(events).set(events.ID, DSL.inline(null, SQLDataType.UUID))
        operationName(query) shouldBe "UPDATE"
        queryTarget(query) shouldBe "model_events"
    }

    test("names a query object insert") {
        val query = ctx.insertQuery(events)
        operationName(query) shouldBe "INSERT"
        queryTarget(query) shouldBe "model_events"
    }

    test("names a query object delete") {
        val query = ctx.deleteQuery(events)
        operationName(query) shouldBe "DELETE"
        queryTarget(query) shouldBe "model_events"
    }

    test("a merge upsert names the operation but has no target") {
        // org.jooq.impl.MergeUpsert implements org.jooq.Merge but not QOM.Merge, so the two functions
        // match on independent hierarchies here. Pinned rather than papered over: the span is named
        // MERGE with no db.collection.name, and eva itself builds no merges.
        val query = ctx.mergeInto(events, events.ID).values(DSL.inline(null, SQLDataType.UUID))
        operationName(query) shouldBe "MERGE"
        queryTarget(query) shouldBe null
    }

    test("names a select") {
        operationName(ctx.selectFrom(events)) shouldBe "SELECT"
    }
})
