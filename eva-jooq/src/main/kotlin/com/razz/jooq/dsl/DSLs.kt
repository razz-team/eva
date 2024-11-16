package com.razz.jooq.dsl

import org.jooq.Field
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.jooq.impl.DSL.array

object ArrayDSL {

    fun arrayContains(field: Field<Array<String>>, vararg values: String) =
        DSL.condition("{0} @> {1}::text[]", field, array(values))

    inline fun <reified T> arrayContains(field: Field<Array<T>>, values: List<T>) =
        DSL.condition("{0} @> {1}", field, array(*values.toTypedArray()))
}

object JsonDSL {

    fun jsonbField(field: Field<JSONB>, name: String): Field<String> =
        DSL.field("{0}->>{1}", String::class.java, field, DSL.inline(name))

    fun jsonbContainsKeys(field: Field<JSONB>, vararg keys: String) =
        DSL.condition("jsonb_exists_all({0}, {1}::text[])", field, array(keys))

    fun jsonbStringEq(field: Field<JSONB>, name: String, value: String) =
        DSL.field("{0}->>{1}", String::class.java, field, name).eq(value)

    fun jsonbStringValue(field: Field<JSONB>, name: String): Field<String> =
        DSL.field("{0}->>{1}", String::class.java, field, name)

    fun jsonbContains(field: Field<JSONB>, value: JSONB) =
        DSL.condition("{0} @> {1}", field, value)
}

object SqlDSL {
    inline fun <reified T> Field<T>.eqAny(values: List<T>) = if (values.size <= 3) {
        this.`in`(values)
    } else {
        this.eq(DSL.any(*values.toTypedArray()))
    }
    inline fun <reified T> Field<T>.eqAny(vararg values: T) = if (values.size <= 3) {
        this.`in`(*values)
    } else {
        this.eq(DSL.any(*values))
    }
}
