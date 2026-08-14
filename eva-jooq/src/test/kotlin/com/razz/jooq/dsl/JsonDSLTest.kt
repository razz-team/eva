package com.razz.jooq.dsl

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.jooq.JSONB
import org.jooq.SQLDialect
import org.jooq.impl.DSL

class JsonDSLTest : FunSpec({

    val ctx = DSL.using(SQLDialect.POSTGRES)
    val refs = DSL.field(DSL.name("refs"), JSONB::class.java)

    test("jsonbField inlines the key") {
        ctx.render(JsonDSL.jsonbField(refs, "idempotencyKey")) shouldBe """"refs"->>'idempotencyKey'"""
    }

    test("jsonbIntField inlines the key") {
        ctx.render(JsonDSL.jsonbIntField(refs, "attempt")) shouldBe """("refs"->>'attempt')::int"""
    }

    test("jsonbPath inlines both keys") {
        ctx.render(JsonDSL.jsonbPath(refs, "payload", "type")) shouldBe """"refs"->'payload'->>'type'"""
    }

    test("jsonbStringEq inlines the key so an expression index stays matchable") {
        ctx.render(JsonDSL.jsonbStringEq(refs, "idempotencyKey", "abc")) shouldBe
            """"refs"->>'idempotencyKey' = ?"""
    }

    test("jsonbStringEq keeps the value bound") {
        ctx.render(JsonDSL.jsonbStringEq(refs, "idempotencyKey", "abc")) shouldNotContain "'abc'"
    }

    test("jsonbStringEq escapes an inlined key") {
        ctx.renderInlined(JsonDSL.jsonbStringEq(refs, "' or true --", "abc")) shouldBe
            """"refs"->>''' or true --' = 'abc'"""
    }

    test("jsonbStringIn inlines the key and binds the values") {
        ctx.render(JsonDSL.jsonbStringIn(refs, "clientFlow", listOf("INARI", "OTHER"))) shouldBe
            """"refs"->>'clientFlow' in (?, ?)"""
    }

    test("jsonbStringIn switches to = any past three values, keeping one bind") {
        ctx.render(JsonDSL.jsonbStringIn(refs, "clientFlow", listOf("a", "b", "c", "d"))) shouldBe
            """"refs"->>'clientFlow' = any (cast(? as varchar[]))"""
    }

    test("jsonbTextArray inlines the key") {
        ctx.render(JsonDSL.jsonbTextArray(refs, "approvers")) shouldBe
            "coalesce((select array_agg(elem) from jsonb_array_elements_text(\"refs\"->'approvers') " +
            "as t(elem)), '{}')"
    }

    test("jsonbContainsKeys binds a flat key array") {
        ctx.render(JsonDSL.jsonbContainsKeys(refs, "a", "b")) shouldBe
            """(jsonb_exists_all("refs", array[?, ?]::text[]))"""
    }

    test("jsonbArrayContainsAny inlines the key and binds the values") {
        ctx.render(JsonDSL.jsonbArrayContainsAny(refs, "tags", listOf("x", "y"))) shouldBe
            """(jsonb_exists_any("refs"->'tags', array[?, ?]::text[]))"""
    }

    test("jsonbArrayContainsAny binds a value that would break a JSON literal") {
        ctx.renderInlined(JsonDSL.jsonbArrayContainsAny(refs, "tags", listOf("""a"]"""))) shouldBe
            """(jsonb_exists_any("refs"->'tags', array['a"]']::text[]))"""
    }
})
