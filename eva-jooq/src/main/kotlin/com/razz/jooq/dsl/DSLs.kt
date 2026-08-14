package com.razz.jooq.dsl

import com.razz.jooq.dsl.SqlDSL.eqAny
import org.jooq.Condition
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.jooq.impl.DSL.array

object ArrayDSL {

    fun arrayContains(field: Field<Array<String>>, vararg values: String) =
        DSL.condition("{0} @> {1}::text[]", field, array(*values))

    inline fun <reified T> arrayContains(field: Field<Array<T>>, values: Collection<T>) =
        DSL.condition("{0} @> {1}", field, array(*values.toTypedArray()))
}

object JsonDSL {

    /**
     * `field->>'name'`, with the key inlined as a SQL literal.
     *
     * The key has to be inlined for a btree expression index such as
     * `CREATE INDEX ... ON t ((refs ->> 'idempotencyKey'))` to be usable. PostgreSQL matches an index
     * expression against a query expression structurally, and a bind parameter is not a constant. Under a
     * generic plan, which a pooled connection reaches after five executions with `plan_cache_mode = auto`,
     * a bound key stays a parameter and the index is silently dropped from consideration.
     *
     * Inlining costs one prepared statement and one plan cache entry per distinct key, so callers must pass
     * keys from a closed set of constants, never keys derived from user input. `DSL.inline` escapes the
     * literal, so the constraint is plan cache size, not injection safety.
     *
     * This accessor is the only place a key gets inlined. Build conditions from it with the jOOQ operators
     * and do not write another `{0}->>{1}` template. That is how the bound key kept coming back.
     */
    fun jsonbField(field: Field<JSONB>, name: String): Field<String> =
        DSL.field("{0}->>{1}", String::class.java, field, DSL.inline(name))

    /** `(field->>'name')::int`. The key is inlined, see [jsonbField]. */
    fun jsonbIntField(field: Field<JSONB>, name: String): Field<Int> =
        DSL.field("({0}->>{1})::int", Int::class.java, field, DSL.inline(name))

    /** `field->'jsonKey'->>'textKey'`. Both keys are inlined, see [jsonbField]. */
    fun jsonbPath(field: Field<JSONB>, jsonKey: String, textKey: String): Field<String> =
        DSL.field("{0}->{1}->>{2}", String::class.java, field, DSL.inline(jsonKey), DSL.inline(textKey))

    /**
     * `field->>'name' = value`, with the key inlined and the value bound.
     *
     * See [jsonbField] for why the key is inlined. The value stays bound because values are unbounded in
     * cardinality and usually user derived, so inlining them would flood the plan cache.
     */
    fun jsonbStringEq(field: Field<JSONB>, name: String, value: String): Condition =
        jsonbField(field, name).eq(value)

    /** `field->>'name'` against a set of values, with the key inlined and the values bound. */
    fun jsonbStringIn(field: Field<JSONB>, name: String, values: Collection<String>): Condition =
        jsonbField(field, name).eqAny(values)

    /**
     * The jsonb array at `field->'name'` as a text list, empty when the key is absent or the array is
     * empty. Callers holding a typed element convert in Kotlin. The SQL does no cast.
     *
     * The key is inlined, see [jsonbField]. This is a correlated subquery per row, so it belongs in a
     * projection, not in a predicate. PostgreSQL raises an error if the value is not a jsonb array.
     */
    fun jsonbTextArray(field: Field<JSONB>, name: String): Field<List<String>> =
        DSL.field(
            "coalesce((select array_agg(elem) from jsonb_array_elements_text({0}->{1}) as t(elem)), '{}')",
            Array<String>::class.java,
            field,
            DSL.inline(name),
        ).convertFrom { it?.toList() ?: listOf() }

    fun jsonbContains(field: Field<JSONB>, value: JSONB): Condition =
        DSL.condition("{0} @> {1}", field, value)

    /**
     * The keys stay bound. A GIN index extracts its query keys at execution time, so it is unaffected by
     * the plan caching behaviour described on [jsonbField].
     */
    fun jsonbContainsKeys(field: Field<JSONB>, vararg keys: String): Condition =
        DSL.condition("jsonb_exists_all({0}, {1}::text[])", field, array(*keys))

    /**
     * True when the jsonb array at `field->'name'` holds any of [values] as a top level string element.
     *
     * The key is inlined, see [jsonbField]. The values are bound as a text array, so a value containing a
     * quote or a backslash is handled by the driver. Nothing builds a JSON string here.
     *
     * An empty [values] matches nothing, which is what "contains any of none" means. Callers that want an
     * absent filter to drop out of the condition have to say so themselves.
     */
    fun jsonbArrayContainsAny(field: Field<JSONB>, name: String, values: Collection<String>): Condition =
        DSL.condition(
            "jsonb_exists_any({0}->{1}, {2}::text[])",
            field,
            DSL.inline(name),
            array(*values.toTypedArray()),
        )
}

object SqlDSL {
    inline fun <reified T> Field<T>.eqAny(values: Collection<T>) = if (values.size <= 3) {
        this.`in`(values)
    } else {
        this.eq(DSL.any(*values.toTypedArray()))
    }
    inline fun <reified T> Field<T>.eqAny(vararg values: T) = if (values.size <= 3) {
        this.`in`(*values)
    } else {
        this.eq(DSL.any(*values))
    }

    /**
     * `field NOT IN (values) OR field IS NULL`.
     *
     * The null branch is deliberate: `NOT IN` on a nullable column drops null rows, which is rarely what
     * the caller means by "everything except these". The name carries the null inclusion so the caller
     * does not have to remember it.
     */
    inline fun <reified T> Field<T>.notEqAllOrNull(values: Collection<T>): Condition {
        val condition = if (values.size <= 3) {
            this.notIn(values)
        } else {
            this.notEqual(DSL.all(*values.toTypedArray()))
        }
        return condition.or(this.isNull)
    }
}
