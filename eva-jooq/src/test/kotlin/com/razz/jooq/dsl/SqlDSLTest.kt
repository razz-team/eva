package com.razz.jooq.dsl

import com.razz.jooq.dsl.SqlDSL.eqAny
import com.razz.jooq.dsl.SqlDSL.notEqAllOrNull
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jooq.SQLDialect
import org.jooq.impl.DSL

class SqlDSLTest : FunSpec({

    val ctx = DSL.using(SQLDialect.POSTGRES)
    val state = DSL.field(DSL.name("state"), String::class.java)

    test("eqAny accepts any collection, not only a list") {
        ctx.render(state.eqAny(setOf("A", "B"))) shouldBe """"state" in (?, ?)"""
    }

    test("eqAny switches to = any past three values") {
        ctx.render(state.eqAny(listOf("A", "B", "C", "D"))) shouldBe """"state" = any (cast(? as varchar[]))"""
    }

    test("notEqAllOrNull keeps null rows") {
        ctx.render(state.notEqAllOrNull(listOf("A", "B"))) shouldBe
            """("state" not in (?, ?) or "state" is null)"""
    }

    test("notEqAllOrNull switches to <> all past three values") {
        ctx.render(state.notEqAllOrNull(listOf("A", "B", "C", "D"))) shouldBe
            """("state" <> all (cast(? as varchar[])) or "state" is null)"""
    }

    test("arrayContains binds a flat array") {
        val tags = DSL.field(DSL.name("tags"), Array<String>::class.java)
        ctx.render(ArrayDSL.arrayContains(tags, "a", "b")) shouldBe """("tags" @> array[?, ?]::text[])"""
    }
})
