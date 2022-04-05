package com.razz.jooq.record

import org.jooq.Record

interface TypedStatefulEntityRecord<ID : Comparable<ID>, S : Enum<S>> : BaseEntityRecord<ID> {

    fun setState(value: S): Record
    val state: S
}
