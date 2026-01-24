package com.razz.jooq.record

import org.jooq.Record

interface TypedStatefulModelRecord<ID : Comparable<ID>, S : Enum<S>> : BaseModelRecord<ID> {

    fun setState(value: S): Record
    val state: S
}
