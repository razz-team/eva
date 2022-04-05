package com.razz.jooq.record

import org.jooq.Record

interface StatefulEntityRecord<ID : Comparable<ID>> : BaseEntityRecord<ID> {

    fun setState(value: String): Record
    val state: String
}
