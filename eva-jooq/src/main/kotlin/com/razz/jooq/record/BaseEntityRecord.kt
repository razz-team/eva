package com.razz.jooq.record

import org.jooq.Record
import java.time.Instant

interface BaseEntityRecord<ID : Comparable<ID>> : Record {

    fun setId(value: ID): Record
    val id: ID

    fun setRecordUpdatedAt(value: Instant): Record
    fun getRecordUpdatedAt(): Instant

    fun setRecordCreatedAt(value: Instant?): Record
    fun getRecordCreatedAt(): Instant

    fun setVersion(value: Long?): Record
    fun getVersion(): Long?
}
