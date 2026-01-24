package com.razz.jooq.record

import org.jooq.Record

interface StatefulModelRecord<ID : Comparable<ID>> : BaseModelRecord<ID> {

    fun setState(value: String): Record
    val state: String
}
