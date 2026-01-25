package com.razz.jooq.record

import org.jooq.Record

/**
 * Base marker interface for Entity JOOQ records.
 *
 * Unlike [BaseModelRecord], entities don't require:
 * - Explicit ID field (identity is derived from composite key or content)
 * - Audit timestamps, version field (no optimistic locking) and no entity lifecycle management ie update()
 */
interface BaseEntityRecord : Record
