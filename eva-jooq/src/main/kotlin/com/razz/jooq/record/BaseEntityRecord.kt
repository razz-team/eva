package com.razz.jooq.record

import org.jooq.Record
import java.time.Instant

/**
 * Base interface for Entity JOOQ records.
 *
 * Unlike [BaseModelRecord], entities don't require:
 * - Explicit ID field (identity is derived from composite key or content)
 * - Version field (no optimistic locking)
 *
 * They do support audit timestamps for tracking.
 */
interface BaseEntityRecord : Record {

    fun setRecordUpdatedAt(value: Instant): Record
    fun getRecordUpdatedAt(): Instant

    fun setRecordCreatedAt(value: Instant?): Record
    fun getRecordCreatedAt(): Instant
}
