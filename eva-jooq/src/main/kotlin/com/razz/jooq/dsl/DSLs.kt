package com.razz.jooq.dsl

import org.jooq.Field
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.jooq.impl.DSL.array

object ArrayDSL {

    fun arrayContains(field: Field<Array<String>>, vararg values: String) =
        DSL.condition("{0} && {1}::text[]", field, array(values))
}

object JsonDSL {

    fun jsonbContainsKey(field: Field<JSONB>, key: String) =
        DSL.field("{0} ? {1}", Boolean::class.java, field, key)

    fun jsonbStringEq(field: Field<JSONB>, name: String, value: String) =
        DSL.field("{0}->>{1}", String::class.java, field, name).eq(value)

    fun jsonbStringValue(field: Field<JSONB>, name: String): Field<String> =
        DSL.field("{0}->>{1}", String::class.java, field, name)

    fun jsonbEq(field: Field<JSONB>, value: JSONB) =
        DSL.condition("{0} @> {1}", field, value)

    fun jsonbContains(field: Field<JSONB>, value: JSONB) =
        DSL.condition("{0} @> {1}", field, value)
}
